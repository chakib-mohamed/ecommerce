# ecommerce-api-gateway/CLAUDE.md

**Spring Boot 3.4.1** (Spring Cloud Gateway 2024.0.0) — this is the only non-Quarkus service. Use `mvn`, not `./mvnw` from the backend root.

## Build & Run

```bash
# From ecommerce-api-gateway/
mvn spring-boot:run -Pdev          # dev profile (routes to localhost ports, CORS from localhost:3000)
mvn clean package -PbuildDocker    # builds Docker image
```

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
| `/api/payments/**`                                      | price-service:8080        |

All routes strip the `/api` prefix before forwarding.
