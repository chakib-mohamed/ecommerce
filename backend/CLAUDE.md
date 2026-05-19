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

## Service-Specific Details

See subdirectory CLAUDE.md files:
- `products-service/CLAUDE.md` — Liquibase, MinIO, JSON naming, Kafka topics
- `authenticate-service/CLAUDE.md` — MongoDB Panache, jBCrypt, JWT config
- `ecommerce-api-gateway/CLAUDE.md` — Spring Boot gateway, routing, CORS
