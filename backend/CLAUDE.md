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
- Extend `FunctionalException` (abstract base in `control/`, carries `Response.Status`)
- Examples: `CartNotFoundException` (404), `CartEmptyException` (400)
- One class per error case; name describes the domain problem, not the HTTP code

**Technical exceptions** — unexpected infrastructure or programming errors:
- Let them propagate as `RuntimeException`; the global handler logs and returns 500

**Global handler** — one `GlobalExceptionHandler` in `boundary/` per service:
- `@ServerExceptionMapper` on `FunctionalException` → `{ type: "FUNCTIONAL", message }` + correct status
- `@ServerExceptionMapper` on `Exception` → `{ type: "TECHNICAL", message: "An unexpected error occurred" }` + logs

**`ErrorResponse`** DTO lives in `boundary/`: `{ String type, String message }`.

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

Testcontainers spins up real Postgres/Mongo/Kafka containers — Docker must be running.

## Service-Specific Details

See subdirectory CLAUDE.md files:
- `products-service/CLAUDE.md` — Liquibase, MinIO, JSON naming, Kafka topics
- `authenticate-service/CLAUDE.md` — MongoDB Panache, jBCrypt, JWT config
- `ecommerce-api-gateway/CLAUDE.md` — Spring Boot gateway, routing, CORS
