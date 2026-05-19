# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Microservices-based ecommerce platform. Backend has 7 services: one Spring Boot API Gateway plus 6 Quarkus services. Frontend is React 18 + TypeScript + Vite.

## Architecture

| Service                   | Framework         | Port | Database   | Notes                              |
|---------------------------|-------------------|------|------------|-------------------------------------|
| ecommerce-api-gateway     | Spring Boot 3.4.1 | 8080 | Redis      | Spring Cloud Gateway; JWT + CORS   |
| authenticate-service      | Quarkus 3.17.6    | 8081 | MongoDB    | JWT auth, jBCrypt passwords         |
| products-service          | Quarkus 3.17.6    | 8082 | PostgreSQL | Kafka producer, MinIO image storage |
| featured-products-service | Quarkus 3.17.6    | 8083 | MongoDB    | Kafka consumer                      |
| orders-service            | Quarkus 3.17.6    | 8084 | MongoDB    |                                     |
| price-service             | Quarkus 3.17.6    | 8085 | MongoDB    |                                     |

Shared API modules: `products-api` and `orders-api` (DTOs only, no runtime).

All traffic goes through the gateway at `/api/**`. Two Docker Compose networks: `frontend` (gateway + frontend) and `backend` (all internal services).

## Build & Run

```bash
./build.sh          # builds frontend image + all backend JARs + Docker images
./run.sh docker     # docker-compose up
./shutdown.sh docker

./run.sh kubernetes   # Kubernetes path (secondary)
./shutdown.sh kubernetes
```

See `backend/CLAUDE.md` for per-service build commands and Quarkus specifics.
See `frontend/CLAUDE.md` for frontend dev commands.

## Development Workflow

Every feature follows this sequence — **do not skip steps or proceed without explicit user approval at each gate**:

1. **Spec** — write or update `docs/specs/<feature>.md`
2. **OpenAPI** — write the full OpenAPI contract (`openapi.yaml`) and show it to the user. **Stop and wait for approval before writing any code.**
3. **Failing tests** — write all tests (they must fail). Show the failure output to the user. **Stop and wait for approval before implementing.**
4. **Implementation** — make the tests pass.
5. **Gateway routes** — update `application.yml` and `application-dev.yml` if new endpoints are exposed.

Feature specs live in `docs/specs/` — one file per feature or cross-service concern.

See `docs/specs/cart.md` for an example of the spec format (OpenAPI-first + TDD).

## Package Conventions

All DTOs (request/response objects, value objects, commands) live in `boundary/dto/` within each service or shared-api module. The `entity/` package is reserved exclusively for persistence-annotated domain objects (Panache entities and their embedded value objects).

## Branch Naming

Use `feature/`, `fix/`, or `chore/` prefixes — e.g., `feature/add-cart`, `fix/auth-token-refresh`, `chore/update-deps`.
