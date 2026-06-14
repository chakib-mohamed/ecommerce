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
make build          # mvn package (skip tests) + all Docker images
make up             # full stack (infra + backend + frontend)
make down           # stop & remove everything

make infra          # just infra containers (db/kafka/etc.)
make backend        # infra + backend services
make front          # full stack (infra + backend + frontend)
make logs           # tail logs for all services
```

Run `make help` for the full target list (including the `dev-*` hot-reload targets).
The `kubernetes/` manifests are stale (they reference a removed `eureka-server`) and are
not wired into the Makefile — the K8s path is a separate follow-up.

See `backend/CLAUDE.md` for backend build commands, testing conventions, and framework specifics.
See `frontend/CLAUDE.md` for frontend dev commands.

### Observability

Distributed tracing + metrics across all 7 backend services. The local stack — **OTel Collector +
Jaeger + Prometheus + Grafana** — runs under the `observability` Docker Compose profile (`make
observability`, also folded into `make up`):

| UI | URL | Purpose |
|----|-----|---------|
| Jaeger | http://localhost:16686 | traces |
| Prometheus | http://localhost:9090 | metrics + targets |
| Grafana | http://localhost:3000 | dashboards (anon admin; *Ecommerce Overview* + *Ecommerce Business KPIs* auto-provisioned) |

- Every service exports OTLP to `otel-collector:4317`; the Collector **tail-samples** (keeps all
  error/slow traces, ~10% of the rest) and forwards to Jaeger. One request is one connected trace
  across the gateway, downstream HTTP calls, **and** Kafka (including outbox-published events).
- Metrics are scraped by Prometheus from each service (gateway `/actuator/prometheus`, Quarkus
  `/q/metrics`) — config in `observability/prometheus.yml`; Grafana provisioning in
  `observability/grafana/provisioning/`.
- Logs carry `traceId`/`spanId` (pivot logs → Jaeger). **`X-Request-ID` is retired** — the gateway
  echoes the trace id back as an `X-Trace-Id` response header.
- Beyond the auto-instrumented RED/JVM/Kafka signals, the four business-owning Quarkus services
  (`authenticate`, `products`, `orders`, `price`) record curated **functional/business meters** in
  their control layer (orders/revenue, auth success/failure, catalog mutations, pricing/discounts),
  surfaced on the *Ecommerce Business KPIs* dashboard.

Specs: `docs/specs/observability.md`, `docs/specs/functional-metrics.md`. Logging/correlation rules: `docs/conventions/logging-conventions.md`.

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

## Branch Naming

Use `feature/`, `fix/`, or `chore/` prefixes — e.g., `feature/add-cart`, `fix/auth-token-refresh`, `chore/update-deps`.

## Git Push Policy

**Never push to a remote without explicit user approval.** `git push` (and any push-equivalent such as `git push --force`, `gh pr merge`, or creating/pushing a PR branch) is always a manual, user-approved step.

- Do not push automatically after committing, even when the user approved the commit — committing and pushing are separate approvals.
- When work is ready to push, stop and ask; state the branch and remote, and wait for an explicit go-ahead.
- Approval for one push does not carry over to later pushes — ask each time.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Exploration: prefer the custom `explorer` agent

For read-only fan-out searches you delegate, spawn the **custom `explorer`** (lowercase)
agent — it is graph-first by definition. **Do not** route exploration to the built-in
`Explore` (capital E): its prompt is grep-first and cannot be changed. (Plan mode forces
the built-in `Explore` — that path is outside our control; everywhere else, choose
`explorer`.)

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
