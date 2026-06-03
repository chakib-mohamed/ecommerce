# backend/CLAUDE.md

Covers all Quarkus services (Quarkus 3.17.6, Java 21). The API gateway is Spring Boot — see `ecommerce-api-gateway/CLAUDE.md`.

This file is a lean index. Each section states the non-negotiable rules; full detail (code snippets, tables, rationale) lives in `docs/conventions/`.

## Local Dev (hot reload)

Compose profiles are the baseline — `make backend` runs the whole backend in Docker. For a
hot-reload loop, run infra in Docker and the service(s) you're editing **natively in the
foreground**, each in its own terminal:

```bash
make infra            # dependencies (db/kafka/etc.) in Docker
make dev-gateway      # gateway on :8080, Spring `dev` profile → routes to localhost:8081-8085
make dev-products     # the service you're working on: quarkus:dev, live reload
make dev-front        # optional: Vite on :3000, proxies /api → :8080
```

Each `make dev-<svc>` runs `quarkus:dev` and overrides `quarkus.http.port` to match the
gateway dev route — so you only need to start the service(s) you're touching; unrun routes
just fail at the gateway.

| Target              | Port | Gateway paths |
|---------------------|------|---------------|
| `dev-authenticate`  | 8081 | `/api/users/**` |
| `dev-products`      | 8082 | `/api/products/**`, `/api/categories/**`, `/api/promotions/**` |
| `dev-featured`      | 8083 | `/api/products/featured` |
| `dev-orders`        | 8084 | `/api/orders/**`, `/api/cart/**` |
| `dev-price`         | 8085 | `/api/prices/**`, `/api/payments/**` |

Run `make help` for the full target list. The gateway is Spring Boot — see
`ecommerce-api-gateway/CLAUDE.md`.

## Architecture & Packaging

- DTOs live in `boundary/dto/`; `entity/` is only for persistence-annotated domain objects.
- Kafka event payloads live in `control/events/`.
- Reactive stack is banned in every Quarkus service (allowed only in the gateway) — blocking JAX-RS + synchronous Panache only. `quarkus-messaging-kafka` is the one allowed exception.
- Kafka producers use JSON-B `JsonbSerializer`, never the Kafka library's `JsonSerializer`.
- Lombok: `@Data` banned on entities; `@Getter`/`@Setter` on entities; `@Data` allowed on plain DTOs.

See `docs/conventions/architecture-conventions.md` for the reactive ban table and full Lombok rules.

## JSON Serialization

- HTTP JSON: snake_case fields, omit nulls, ISO-8601 dates (never timestamps). Full contract in root `CLAUDE.md`.
- JSON-B endpoints configured via `CustomJsonbConfigCustomizer`; Jackson via `application.properties`.
- Kafka event payloads stay camelCase — the naming strategy does not apply to them.

See `docs/conventions/json-serialization-conventions.md` for config snippets and the annotation-exception rule.

## Exception Handling

- Two-tier model: `FunctionalException` (known domain errors, specific status + `errorCode`) vs technical errors (propagate → 500).
- One `GlobalExceptionHandler` in `boundary/` per service. **Never use try-catch in resource classes.**
- Use Bean Validation (`@Valid`) for structural rules; `FunctionalException` for state-dependent domain errors.

See `docs/conventions/exception-handling-conventions.md` for the handler contract, `ErrorResponse` DTO, and `ConstraintViolationExceptionMapper` rules.

## Persistence & Transactions

- Panache active-record style: static entity methods (`Product.findById(id)`).
- `@Transactional` on control-layer mutating methods only — never on resources. No network I/O inside a transaction (use the outbox pattern for DB+Kafka writes).
- MongoDB single-document writes need no `@Transactional`; Postgres reads usually don't either.
- All JPA relationship fields must declare `fetch = FetchType.LAZY` explicitly.

See `docs/conventions/persistence-conventions.md` for the full transaction and JPA rules.

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

### Git hooks (pre-merge-commit)

One version-controlled hook lives in `.githooks/`. Activate it once per clone:

```bash
sh .githooks/install.sh      # or: git config core.hooksPath .githooks
```

- **`pre-merge-commit`** — gates a merge commit **into `main`** (skips merges into any other branch) and aborts the merge on any violation or test failure. It runs two steps from the backend reactor root, neither needing Docker: (1) Checkstyle + SpotBugs static analysis across all backend modules (`./mvnw -DskipTests compile checkstyle:check spotbugs:check`), then (2) the backend **unit tests** via `./mvnw test -DexcludedGroups=integration`, which excludes everything tagged `@Tag("integration")` (the `@QuarkusTest` / Testcontainers suites). A fast-forward merge creates no merge commit and so skips the hook — use `git merge --no-ff` into `main` to force the gate. Bypass (discouraged) with `git merge --no-verify`. See `docs/conventions/testing-conventions.md` for the `@Tag("integration")` rule that keeps this split working.

## Logging

See `docs/conventions/logging-conventions.md` for logging rules — structured format, correlation IDs via MDC, layer placement, Kafka symmetry, sensitive data, and cross-service elapsed time.

## Service-Specific Details

See subdirectory CLAUDE.md files:
- `products-service/CLAUDE.md` — Liquibase, MinIO, JSON naming, Kafka topics
- `authenticate-service/CLAUDE.md` — MongoDB Panache, jBCrypt, JWT config
- `ecommerce-api-gateway/CLAUDE.md` — Spring Boot gateway, routing, CORS
