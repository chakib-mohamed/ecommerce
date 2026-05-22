# backend/CLAUDE.md

Covers all Quarkus services (Quarkus 3.17.6, Java 21). The API gateway is Spring Boot — see `ecommerce-api-gateway/CLAUDE.md`.

## Build Commands

```bash
# From backend/ directory
./mvnw clean package -DskipTests          # build all services
./mvnw clean package -DskipTests -pl products-service   # single service

# Quarkus dev mode (hot reload, runs on :8080 by default)
./mvnw quarkus:dev -pl products-service
```

## Package Conventions

All DTOs (request/response objects, value objects, commands) live in `boundary/dto/` within each service or shared-api module. The `entity/` package is reserved exclusively for persistence-annotated domain objects (Panache entities and their embedded value objects).

Kafka event payloads live in `control/events/` — they are messaging objects, not HTTP DTOs, so they stay in the control layer.

## Exception Handling

All Quarkus services use a two-tier exception model. **Never use try-catch in resource classes.**

**Functional exceptions** — known domain errors with a specific HTTP status:
- Extend `FunctionalException` (abstract base in `control/exceptions/`, carries `Response.Status`)
- All exception classes live in `control/exceptions/` when a service has more than one
- Examples: `CartNotFoundException` (404), `CartEmptyException` (400)
- One class per error case; name describes the domain problem, not the HTTP code

**Technical exceptions** — unexpected infrastructure or programming errors:
- Let them propagate as `RuntimeException`; the global handler logs and returns 500

**Global handler** — one `GlobalExceptionHandler` in `boundary/` per service:
- `@ServerExceptionMapper` on `FunctionalException` → `{ type: "FUNCTIONAL", message }` + correct status
- `@ServerExceptionMapper` on `Exception` → `{ type: "TECHNICAL", message: "An unexpected error occurred" }` + logs

**`ErrorResponse`** DTO lives in `boundary/dto/`: `{ String type, String message }`.

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

## Dev Profile

When running with `./mvnw quarkus:dev`, the `%dev` profile activates automatically:
- Kafka bootstrap: `localhost:9092`
- MongoDB: `localhost:27017`
- PostgreSQL: `localhost:5432`

These are the local Docker Compose addresses. Ensure infrastructure containers are up before starting dev mode.

## Testing

Tests use JUnit 5 + Mockito + Testcontainers + REST Assured. Run with:

```bash
./mvnw test -pl products-service
```

**Rule: always use Testcontainers — never Quarkus Dev Services — for test infrastructure.**

- Set `%test.quarkus.devservices.enabled=false` in every service's `src/test/resources/application.properties`.
- Do **not** rely on empty connection strings (e.g. `quarkus.mongodb.connection-string=`) to trigger Dev Services.
- Each service that needs a backing store provides a `*TestResource` class implementing `QuarkusTestResourceLifecycleManager`, which starts the real container and injects the connection string.
- Annotate every `@QuarkusTest` class that touches a store with `@QuarkusTestResource(XxxTestResource.class)`.
- Docker must be running for tests to pass.

**Kafka in tests** — use `KafkaTestResource` (backed by `org.testcontainers:kafka`, image `confluentinc/cp-kafka:7.6.1`). It starts a real broker and injects `kafka.bootstrap.servers`. **Never use `smallrye-reactive-messaging-in-memory`** — it is banned from all services. Any `@QuarkusTest` in a service that has Kafka channels must be annotated with `@QuarkusTestResource(KafkaTestResource.class)`.

### Test Method Naming

`action_context_expectedOutcome` — three underscore-separated camelCase segments, no `test` prefix, no `public` modifier.

```java
// good
void createOrder_validProducts_returns201WithCalculatedPrice()
void authenticate_wrongPassword_returns401()
void getUsername_revokedToken_returnsEmpty()

// bad
public void testCreateOrder()
void testAuthenticate_WrongPassword_Returns401()
```

### Test Body Structure

Explicit `// given`, `// when`, `// then` comment blocks in every test. Omit `// given` only when there is genuinely no setup.

```java
@Test
void confirmOrder_initiatedOrder_changesStatusToConfirmed() {
    // given
    Order order = new Order();
    order.setStatus(OrderStatus.INITIATED);
    order.persist();

    // when
    var response = given().when().post("/orders/" + order.id + "/confirm");

    // then
    response.then().statusCode(200).body("status", is("CONFIRMED"));
}
```

### HTTP Test Split

Separate the HTTP call from the assertions so `// when` and `// then` are distinct.

```java
// when
var response = given().contentType(ContentType.JSON).body(request)
        .when().post("/orders");

// then
response.then().statusCode(201).body("price", is(100.0f));
```

### One Behavior Per Test

Do not combine create + update + delete in a single test method. Each test exercises exactly one scenario. Compound setup (e.g. creating a record before testing an update) belongs in `// given`, not as a separate test phase.

### Class Modifiers

Test classes are package-private (no `public`).

## JPA / Entity Rules

All JPA relationship fields (`@ManyToOne`, `@OneToOne`, `@OneToMany`, `@ManyToMany`) **must** declare `fetch = FetchType.LAZY` explicitly. `@ManyToOne` and `@OneToOne` default to `EAGER`, which causes silent N+1 queries.

```java
// good
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private Category parent;

// bad — defaults to EAGER
@ManyToOne
@JoinColumn(name = "parent_id")
private Category parent;
```

Enforce this rule with an ArchUnit test where possible so it becomes a CI gate.

## Lombok Rules

**Never use `@Data`.** It generates `equals`/`hashCode`/`toString`/setters indiscriminately, which breaks entity identity and exposes mutability on config/response objects.

### By class type:

| Class type | Allowed annotations | Rationale |
|------------|-------------------|-----------|
| JPA entity (`PanacheEntity`) | `@Getter` + `@Setter` | No `equals`/`hashCode` override — use JPA identity. Use `@Getter(AccessLevel.NONE)` on fields with manual getter overrides (e.g. lazy-init collections). |
| Panache MongoDB entity (`PanacheMongoEntity`) | **None** — use public fields directly | Panache MongoDB rewrites field access at build time; Lombok annotations are redundant and can conflict. |
| CDI/Spring config bean | `@Getter` only | Config must be immutable after injection — never expose setters. |
| Response DTO (read-only) | `@Getter` + `@AllArgsConstructor` | Immutable by construction; no setters needed. |
| Request DTO (JSON-B input) | `@Getter` + `@Setter` | Needs setters for deserialization. |
| Kafka event payload | `@Getter` + `@Setter` + `@NoArgsConstructor` + `@AllArgsConstructor` | JSON-B needs no-arg constructor for deserialization; all-args for construction in producers. |
| Immutable value object | `@Getter` + `@AllArgsConstructor` (with `final` fields) | True value semantics — no setters. |

### Banned annotations:

| Annotation | Why |
|------------|-----|
| `@Data` | Generates `equals`/`hashCode` on mutable fields, adds unwanted setters, creates `toString` that may trigger lazy-loading |
| `@EqualsAndHashCode` on entities | Entity identity must come from the database ID, not field-based hashing |
| `@ToString` on entities | Can trigger lazy-loading of relationships and leak sensitive data |

### Additional rules:

- **Do not mix public fields with `@Getter`/`@Setter`** — pick one convention. Panache Mongo = public fields (no Lombok); everything else = private fields + Lombok accessors.
- **When overriding a generated getter** (e.g. null-safe collection init), annotate the field with `@Getter(AccessLevel.NONE)` to signal intent and avoid dead-code confusion.
- **Never use `@Setter` on config/security-sensitive fields** (keys, secrets, tokens).


## Service-Specific Details

See subdirectory CLAUDE.md files:
- `products-service/CLAUDE.md` — Liquibase, MinIO, JSON naming, Kafka topics
- `authenticate-service/CLAUDE.md` — MongoDB Panache, jBCrypt, JWT config
- `ecommerce-api-gateway/CLAUDE.md` — Spring Boot gateway, routing, CORS
