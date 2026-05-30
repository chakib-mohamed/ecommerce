# backend/CLAUDE.md

Covers all Quarkus services (Quarkus 3.17.6, Java 21). The API gateway is Spring Boot — see `ecommerce-api-gateway/CLAUDE.md`.

## Package Conventions

All DTOs (request/response objects, value objects, commands) live in `boundary/dto/` within each service or shared-api module. The `entity/` package is reserved exclusively for persistence-annotated domain objects (Panache entities and their embedded value objects).

Kafka event payloads live in `control/events/` — they are messaging objects, not HTTP DTOs, so they stay in the control layer.

## JSON Serialization

All HTTP API JSON must use **snake_case** field names, omit **null** fields, and format dates as **ISO-8601 strings** (never timestamps).

### JSON-B services (all Quarkus HTTP endpoints)

Both the naming strategy and null omission must be set via `CustomJsonbConfigCustomizer` in `boundary/` — there are no equivalent `application.properties` keys for these in Quarkus 3.17.6:
```java
@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {
    @Override
    public void customize(JsonbConfig config) {
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
        config.withNullValues(false);
    }
}
```

JSON-B defaults to ISO-8601 dates — no extra config needed.

### Jackson services (REST clients in shared API modules, price-service HTTP)

Configure in `application.properties`:
```properties
quarkus.jackson.property-naming-strategy=SNAKE_CASE
quarkus.jackson.serialization-inclusion=NON_NULL
quarkus.jackson.write-dates-as-timestamps=false
quarkus.jackson.fail-on-unknown-properties=false
```

### Kafka events

`JsonbSerializer` and `JsonbDeserializer` (used in all Kafka channels) create their own plain Jsonb instance, independent of the CDI-managed one. Kafka event field names therefore remain camelCase regardless of the JSON-B naming strategy configured for HTTP. Do not rely on `LOWER_CASE_WITH_UNDERSCORES` applying to Kafka payloads — use consistent camelCase in `control/events/` classes.

### Annotation exceptions

`@JsonbProperty` / `@JsonProperty` are only permitted to override the naming for a specific field that must deviate from the global strategy (rare). Never add them just to reproduce what the strategy already does.

## Exception Handling

All Quarkus services use a two-tier exception model. **Never use try-catch in resource classes.**

**Functional exceptions** — known domain errors with a specific HTTP status:
- Extend `FunctionalException` (abstract base in `control/exceptions/`, carries `Response.Status` and `errorCode`)
- Constructor signature: `(Response.Status status, String errorCode, String message)`
- All exception classes live in `control/exceptions/` when a service has more than one
- Examples: `CartNotFoundException` (404, `CART_NOT_FOUND`), `CartEmptyException` (400, `CART_EMPTY`)
- One class per error case; name describes the domain problem, not the HTTP code
- `errorCode` is a SCREAMING_SNAKE_CASE string unique within the service — the frontend uses it to identify errors

**Technical exceptions** — unexpected infrastructure or programming errors:
- Let them propagate as `RuntimeException`; the global handler logs and returns 500

**Global handler** — one `GlobalExceptionHandler` in `boundary/` per service:
- `FunctionalException` → `{ type: "FUNCTIONAL", errorCode, message }` + correct status
- `Exception` → `{ type: "TECHNICAL", errorCode: "INTERNAL_ERROR", message: "An unexpected error occurred" }` + logs

**`ErrorResponse`** DTO lives in `boundary/dto/`: `{ String type, String errorCode, String message }`.

## Boundary Validation

Annotate request DTOs with Bean Validation constraints (`@NotNull`, `@Positive`, `@Size`, etc.) and use `@Valid` on the resource method parameter. Use constraints for structural/format rules (null checks, numeric ranges, string patterns). Reserve `FunctionalException` subclasses for domain errors that depend on application state (e.g., duplicate email, insufficient stock).

When a service uses `@Valid` on a resource method, Resteasy intercepts `ConstraintViolationException` before `GlobalExceptionHandler` sees it. Add a dedicated `ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException>` in `boundary/` that returns `{ type: "FUNCTIONAL", errorCode: "VALIDATION_ERROR", message }` + 400. Services that do not use `@Valid` at the resource layer (e.g. `products-service`) do not need this second mapper — the `instanceof ConstraintViolationException` branch in `GlobalExceptionHandler` suffices.

## Reactive Stack Policy

The reactive stack is **only allowed in `ecommerce-api-gateway`** (Spring WebFlux / Project Reactor). All six Quarkus services must use the blocking, imperative stack exclusively.

**Banned in every Quarkus service** — do not add these to any `pom.xml` or import them in any `.java` file:

| What | Examples |
|------|---------|
| Reactive REST layer | `quarkus-resteasy-reactive`, `quarkus-rest` |
| Reactive Panache (ORM) | `quarkus-hibernate-reactive-panache` |
| Reactive Panache (MongoDB) | `quarkus-mongodb-panache-reactive` |
| Reactive routes | `quarkus-reactive-routes` |
| Mutiny library | `quarkus-mutiny`, `io.smallrye.reactive:smallrye-mutiny` |
| In-memory messaging connector | `io.smallrye.reactive:smallrye-reactive-messaging-in-memory` |
| Reactive types in code | `Uni<T>`, `Multi<T>` (Mutiny), `Mono<T>`, `Flux<T>` (Reactor) |

**Allowed** — these are messaging infrastructure, not user-facing reactive APIs:
- `quarkus-messaging-kafka` — Kafka producer/consumer channels

Use blocking JAX-RS (`quarkus-resteasy`) and synchronous Panache for all HTTP endpoints and database access.

## Quarkus-Specific Patterns

**Panache ORM** (`products-service`): Entity methods are static — use `Product.findById(id)`, not instance calls. The active record pattern means queries live on the entity class.

**Panache MongoDB** (`authenticate-service`, `featured-products-service`, `orders-service`, `price-service`): Same active-record style but for MongoDB documents.

**Kafka serialization**: All Kafka producers use `io.quarkus.kafka.client.serialization.JsonbSerializer` (Jakarta JSON-B), not Jackson. Don't switch to `JsonSerializer` from the Kafka library.

## Testing

Tests use JUnit 5 + Mockito + Testcontainers + REST Assured. Run with:

```bash
./mvnw test -pl products-service
```

**Rule: never run `mvn test` or `mvn verify` from the parent `backend/` directory without `-pl`.** Always target one service at a time. Running all services in parallel saturates Docker (every service starts its own Testcontainers) and causes hangs or timeouts.

```bash
# Correct — one service at a time
./mvnw test    -pl products-service
./mvnw verify  -pl orders-service

# Wrong — do not do this
./mvnw test     # runs all services in parallel → Docker overload
./mvnw verify   # same problem
```

See `docs/conventions/testing-conventions.md` for all test conventions — including Testcontainers vs Dev Services, Kafka test setup, and container reuse, plus naming, display names, body structure, split conventions, and coverage requirements.

## Database Transaction Rules

### Transaction boundary

`@Transactional` belongs on control-layer (service) methods that mutate state — **never on resource (REST endpoint) classes**. This mirrors the exception-handling pattern: resources orchestrate, control layers own the work.

### No I/O inside a transaction

Never perform HTTP calls, Kafka produces, or any external network I/O inside a `@Transactional` method. For services that write to both a database and Kafka (e.g. `products-service`), use the transactional outbox pattern: persist the event record inside the transaction, publish to Kafka only after the commit succeeds.

### PostgreSQL (`products-service`)

`jakarta.transaction.Transactional` (JTA) has no `readOnly` attribute — that is Spring-only. For truly read-only methods that do not need session continuity across multiple queries, omit `@Transactional` entirely; Panache manages single-query reads automatically. Only add `@Transactional` when the method must keep the Hibernate session open across two or more queries (e.g. multi-bag join workarounds).

### MongoDB (all other Quarkus services)

Single-document operations do not need `@Transactional` — MongoDB's document model provides atomic single-document writes natively. Only add `@Transactional` when you need multi-document atomicity, and only when the MongoDB deployment supports sessions (replica set required). Do not add it by default.

### Rollback

Let unchecked exceptions propagate to trigger automatic rollback. Never catch-and-ignore inside a `@Transactional` method. Consistent with the two-tier `FunctionalException` / `GlobalExceptionHandler` model — functional errors should still propagate out and trigger rollback before the global handler maps them to a response.

### Keep transactions short

Validate inputs and authorise the request *before* opening a transaction. Do not include slow computations, bulk reads for display, or anything that can be done outside the write path.

## JPA / Entity Rules

All JPA relationship fields (`@ManyToOne`, `@OneToOne`, `@OneToMany`, `@ManyToMany`) **must** declare `fetch = FetchType.LAZY` explicitly. `@ManyToOne` and `@OneToOne` default to `EAGER`, which causes silent N+1 queries. Enforce with an ArchUnit test where possible so it becomes a CI gate.

## Lombok Rules

**`@Data` is banned on entity classes.** It generates `equals`/`hashCode`/`toString`/setters indiscriminately, which breaks entity identity. On plain DTOs (control-layer objects, boundary DTOs) `@Data` is permitted.

### By class type:

| Class type | Allowed annotations | Rationale |
|------------|-------------------|-----------|
| JPA entity (`PanacheEntity`) | `@Getter` + `@Setter` | No `equals`/`hashCode` override — use JPA identity. Use `@Getter(AccessLevel.NONE)` on fields with manual getter overrides (e.g. lazy-init collections). |
| Panache MongoDB entity (`PanacheMongoEntity`) | `@Getter` + `@Setter` | Same convention as JPA entities — private fields with accessors. |
| CDI/Spring config bean | `@Getter` only | Config must be immutable after injection — never expose setters. |
| Plain DTO (control-layer, boundary) | `@Data` permitted | No entity identity concerns; `@Data` is acceptable shorthand. |
| Kafka event payload | `@Getter` + `@Setter` + `@NoArgsConstructor` + `@AllArgsConstructor` | JSON-B needs no-arg constructor for deserialization; all-args for construction in producers. |
| Immutable value object | `@Getter` + `@AllArgsConstructor` (with `final` fields) | True value semantics — no setters. |

### Banned annotations:

| Annotation | Why |
|------------|-----|
| `@Data` on entity classes | Generates `equals`/`hashCode` on mutable fields, breaks entity identity, `toString` can trigger lazy-loading |
| `@EqualsAndHashCode` on entities | Entity identity must come from the database ID, not field-based hashing |
| `@ToString` on entities | Can trigger lazy-loading of relationships and leak sensitive data |

### Additional rules:

- **Do not mix public fields with `@Getter`/`@Setter`** — pick one convention. Panache Mongo = public fields (no Lombok); everything else = private fields + Lombok accessors.
- **When overriding a generated getter** (e.g. null-safe collection init), annotate the field with `@Getter(AccessLevel.NONE)` to signal intent and avoid dead-code confusion.
- **Never use `@Setter` on config/security-sensitive fields** (keys, secrets, tokens).


## Logging

See `docs/conventions/logging-conventions.md` for logging rules — structured format, correlation IDs via MDC, layer placement, Kafka symmetry, sensitive data, and cross-service elapsed time.

## Service-Specific Details

See subdirectory CLAUDE.md files:
- `products-service/CLAUDE.md` — Liquibase, MinIO, JSON naming, Kafka topics
- `authenticate-service/CLAUDE.md` — MongoDB Panache, jBCrypt, JWT config
- `ecommerce-api-gateway/CLAUDE.md` — Spring Boot gateway, routing, CORS
