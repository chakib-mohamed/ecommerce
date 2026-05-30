# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project-Specific Details

> **Fill this section in per-repository. Everything below the line is generic and portable.**

### Overview

Microservices-based ecommerce platform. Backend has 7 services: one Spring Boot API Gateway plus 6 Quarkus services. Frontend is React 18 + TypeScript + Vite.

### Architecture

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

### Build & Run

```bash
./build.sh          # builds frontend image + all backend JARs + Docker images
./run.sh docker     # docker-compose up
./shutdown.sh docker

./run.sh kubernetes   # Kubernetes path (secondary)
./shutdown.sh kubernetes
```

See `backend/CLAUDE.md` for backend build commands, testing conventions, and framework specifics.
See `frontend/CLAUDE.md` for frontend dev commands.

### JSON Serialization Rules

All HTTP API JSON across every service must follow these rules:

| Rule | Detail |
|------|--------|
| Field names | **snake_case** — `access_token`, `user_id`, `created_at` |
| Null fields | **Omitted** — never serialized as `null` |
| Dates | **ISO-8601 strings** — `2026-01-15T10:30:00`, never numeric timestamps |
| Unknown keys | **Ignored on deserialization** — forward-compatible |

Implementation: see `docs/conventions/json-serialization-conventions.md` for how to configure each framework (JSON-B vs Jackson vs Spring Jackson).

---

## Development Workflow

Every feature follows this sequence — **do not skip steps or proceed without explicit user approval at each gate**:

1. **Spec** — write or update `docs/specs/<feature>.md`
2. **OpenAPI** — write the full OpenAPI contract (`openapi.yaml`) and show it to the user. **Stop and wait for approval before writing any code.**
   - Descriptions must be consumer-facing only: what the endpoint does, what inputs it expects, what it returns.
   - Never mention database technology, message brokers, internal service calls, event names, framework names, or infrastructure details in any `info`, `summary`, `description`, or schema `description` field.
3. **Failing tests** — write all tests (they must fail). Show the failure output to the user. **Stop and wait for approval before implementing.**
4. **Implementation** — make the tests pass.
5. **Infrastructure config** — update routing/gateway/proxy config if new endpoints are exposed.

Feature specs live in `docs/specs/` — one file per feature or cross-service concern.

## Docs Layout

All documentation artifacts live under `docs/`:

| Path | Contents |
|------|----------|
| `docs/specs/` | Feature specs (one file per feature) |
| `docs/tasks/` | Task plans and to-do lists — named `<feature>-plan.md` / `<feature>-todo.md` |
| `docs/reviews/` | Code review notes |
| `docs/conventions/` | Backend coding conventions (architecture, JSON, exceptions, persistence, testing, logging) |

Never place documentation files at the repo root or in any other directory.

## Task Session Policy

Each task in `docs/tasks/` must be implemented in its own dedicated Claude Code session, and each **phase within a task** must also be its own session. Do not carry over context from a previous task or phase — start fresh each time to keep context lean and avoid cross-task interference.

## Branch Naming

Use `feature/`, `fix/`, or `chore/` prefixes — e.g., `feature/add-cart`, `fix/auth-token-refresh`, `chore/update-deps`.

## Git Push Policy

**Never push to a remote without explicit user approval.** `git push` (and any push-equivalent such as `git push --force`, `gh pr merge`, or creating/pushing a PR branch) is always a manual, user-approved step.

- Do not push automatically after committing, even when the user approved the commit — committing and pushing are separate approvals.
- When work is ready to push, stop and ask; state the branch and remote, and wait for an explicit go-ahead.
- Approval for one push does not carry over to later pushes — ask each time.
