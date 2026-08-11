# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project-Specific Details

> **Fill this section in per-repository. Everything below the line is generic and portable.**

### Overview

Microservices-based ecommerce platform. Backend has 8 services: one Spring Boot API Gateway plus 7 Quarkus services. Frontend is React 18 + TypeScript + Vite.

### Architecture

Framework versions live in the poms — the gateway is the only Spring Boot service, every other one is Quarkus.

| Service                   | Port | Database   | Notes                                    |
|---------------------------|------|------------|------------------------------------------|
| ecommerce-api-gateway     | 8080 | Redis      | Spring Cloud Gateway; JWT + CORS         |
| authenticate-service      | 8081 | MongoDB    | JWT auth, jBCrypt passwords              |
| products-service          | 8082 | PostgreSQL | Kafka producer, LocalStack S3 image storage |
| featured-products-service | 8083 | MongoDB    | Kafka consumer                           |
| orders-service            | 8084 | MongoDB    |                                          |
| price-service             | 8085 | MongoDB    |                                          |
| analytics-service         | 8086 | PostgreSQL | Kafka consumer; read-model warehouse     |
| payment-service           | 8087 | PostgreSQL | Kafka only — no gateway route; charges via Stripe |

Shared API modules: `products-api` and `orders-api` (DTOs only, no runtime). `outbox-common` is
shared runtime code — the relay base class and trace propagation — plus the saga's wire-contract
fixtures, published as a test-jar so they stay off every runtime classpath.

Ports in the table are **host** ports, published by the compose mapping. Every service listens on
**8080 inside its container**; setting a service's `quarkus.http.port` to its host port instead
publishes nothing, fails the healthcheck, and leaves Prometheus scraping a dead port.

All traffic goes through the gateway at `/api/**`, except payment-service, which has no HTTP API
and is reached only over Kafka. Two Docker Compose networks: `frontend` (gateway + frontend) and
`backend` (all internal services).

**Adding a service means editing lists that do not reference each other**, and nothing checks them:
`docker-compose.yml` (including `KAFKA_BOOTSTRAP_SERVERS` for anything using Kafka), the image list
in `make build-api`, the two fallback service lists in `.github/workflows/ci.yml`, the mapping in
`.github/scripts/affected-services.sh`, the Prometheus scrape config, and this table. payment-service
was missing from four of them at once.

### Build & Run

Run `make help` for the full target list (including the `dev-*` hot-reload targets).
The `kubernetes/` manifests are stale (they reference a removed `eureka-server`) and are
not wired into the Makefile — the K8s path is a separate follow-up.

**Demo data is opt-in, and the Compose stack opts in.** `make up` seeds the demo catalogue and two
demo accounts (`admin@ecommerce.test` / `retail@ecommerce.test`, passwords committed in
`backend/dev-scripts/mongodb/03-users.js`) because this stack is for development and the e2e suite
needs them. `make up-bare` runs the same stack with neither: an empty catalogue and no accounts.

The two are controlled separately — `LIQUIBASE_CONTEXTS` for the catalogue, `SEED_DEMO_DATA` for
images and accounts — because Compose cannot derive one from the other, which is why `up-bare`
exists rather than a bare variable. **The applications' own defaults are the safe ones**: the seed
changesets are tagged `context:seed` and the default context asks only for schema, so a deployment
that sets nothing gets no demo data. Note the direction of the danger — Liquibase applies *every*
changeset when no context is set at runtime, so the guard is the application always naming one,
not the tag by itself.

See `backend/CLAUDE.md` for backend build commands, testing conventions, and framework specifics.
See `frontend/CLAUDE.md` for frontend dev commands.

### Observability

Distributed tracing, metrics, and logs across all 8 backend services, via **OTel Collector + Jaeger +
Prometheus + Alertmanager + Loki + Grafana** under the `observability` Compose profile
(`make observability`, also folded into `make up`). One request is one connected trace across the
gateway, downstream HTTP calls, and Kafka. **`X-Request-ID` is retired** — the gateway echoes the
trace id back as `X-Trace-Id`.

Alerts are delivered, not just evaluated: Alertmanager groups and routes them, and the default
receiver posts to a local sink so the path is verifiable. **The push destination is deliberately
unset** — Slack and email receivers are written and commented in `observability/alertmanager.yml`,
and both read their credential from a mounted file, never a committed value.

Dashboard URLs, sampling behavior, the log↔trace pivot, and the per-service wiring live in the
`observability` skill. Specs: `docs/specs/observability.md`, `docs/specs/functional-metrics.md`,
`docs/specs/log-aggregation.md`. Logging/correlation rules: `docs/conventions/logging-conventions.md`.

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
| `docs/adr/` | Architecture Decision Records — numbered `NNNN-<slug>.md`, one accepted decision per file; see `docs/adr/README.md` |

Never place documentation files at the repo root or in any other directory.

## Task Session Policy

Each task in `docs/tasks/` must be implemented in its own dedicated Claude Code session, and each **phase within a task** must also be its own session. Do not carry over context from a previous task or phase — start fresh each time to keep context lean and avoid cross-task interference.

## Search & Exploration

Delegate fan-out searches to the `explorer` agent rather than running Grep/Glob/Read inline, unless you already know the exact file/line or the result feeds an edit in the same step.

## Branch Naming

Use `feature/`, `fix/`, or `chore/` prefixes — e.g., `feature/add-cart`, `fix/auth-token-refresh`, `chore/update-deps`.

## Commit Authorship

Commits are authored by the repository owner and committed by the agent. Pass the author
explicitly on every commit:

```bash
git commit --author="chakib mohamed <chakib.mohamed2@gmail.com>" -m "..."
```

Leave `user.name` / `user.email` as `Claude <noreply@anthropic.com>` — that is the **committer**
line, and the SSH signature is issued against it. Putting the owner's address there instead breaks
signature verification and GitHub marks the commit Unverified.

Keep the `Co-Authored-By: Claude ...` trailer; with the author line now the owner's, the trailer is
what records the agent's involvement.

## Git Push Policy

**Never push to a remote without explicit user approval.** `git push` (and any push-equivalent such as `git push --force`, `gh pr merge`, or creating/pushing a PR branch) is always a manual, user-approved step.

- Do not push automatically after committing, even when the user approved the commit — committing and pushing are separate approvals.
- When work is ready to push, stop and ask; state the branch and remote, and wait for an explicit go-ahead.
- Approval for one push does not carry over to later pushes — ask each time.
