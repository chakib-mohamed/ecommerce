# ecommerce-api-gateway/CLAUDE.md

**Spring Boot 3.4.1** (Spring Cloud Gateway 2024.0.0) — this is the only non-Quarkus service. Use `mvn`, not `./mvnw` from the backend root.

## Build & Run

```bash
make dev-gateway                                   # from repo root (preferred) — dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev # from ecommerce-api-gateway/ — dev profile
mvn clean package -PbuildDocker                    # builds Docker image
```

The `dev` profile (`application-dev.yml`) routes to `localhost:8081–8085` and allows CORS
from `localhost:3000`. There is **no Maven `dev` profile** — activate the Spring profile with
`-Dspring-boot.run.profiles=dev` (not `-Pdev`).

## Responsibilities

- **Routing**: strips `/api` prefix, forwards to internal services (see `application.yml` for route table)
- **JWT validation**: validates tokens issued by `authenticate-service` before forwarding requests
- **CORS**: configured per-profile; dev profile allows `http://localhost:3000`
- **Circuit breaking**: Resilience4j via `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- **Tracing**: Zipkin/Brave (`zipkin-reporter-brave`)
- **Session store**: Redis (`spring.data.redis.*`)

## Dev Profile (`application-dev.yml`)

- Routes point to `localhost:808x` ports instead of Docker hostnames
- HTTP wiretap enabled (`httpclient.wiretap: true`, `httpserver.wiretap: true`) — logs full request/response at TRACE level; produces verbose output, disable for normal debugging

## Route Table (from `application.yml`)

| Path pattern                                            | Upstream service          |
|---------------------------------------------------------|---------------------------|
| `/api/users/**`                                         | authenticate-service:8080 |
| `/api/products/featured`                                | featured-products-service:8080 |
| `/api/products/**`, `/api/categories/**`, `/api/promotions/**` | products-service:8080 |
| `/api/orders/**`                                        | orders-service:8080       |
| `/api/pricing/**`                                       | price-service:8080        |

All routes strip the `/api` prefix before forwarding.
