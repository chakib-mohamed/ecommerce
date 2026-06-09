# ADR-0006: Make + Docker Compose profiles as the build/run interface

- **Status:** Accepted
- **Date:** 2026-06-03
- **Deciders:** CHAKIB Mohamed
- **Related:** `Makefile`, `docker-compose.yml`, `docs/tasks/build-run-modernization-plan.md`

## Context

The project was driven by a set of hand-written `build`/`run`/`shutdown` shell scripts. With seven
backend services plus a frontend, infrastructure (MongoDB, PostgreSQL, Kafka, Redis, LocalStack),
and several start-up shapes (infra only, infra + backend, full stack, hot-reload dev), the scripts
were duplicative and the available commands were not discoverable. We wanted a single, documented
entry point for building and running the stack, and a way to bring up **subsets** of it without
separate compose files.

## Decision drivers

- **One discoverable interface** for build/run/teardown (`make help` lists everything).
- Bring up **slices** of the stack (just infra, infra+backend, full stack) without maintaining
  multiple compose files.
- Keep `make infra` lean so day-to-day inner-loop work doesn't start the whole platform.

## Decision

Adopt a **Makefile as the single build/run interface**, backed by **Docker Compose profiles**:

1. Replace the `build`/`run`/`shutdown` scripts with `make` targets — `build`, `up`, `down`,
   `infra`, `backend`, `front`, `logs`, plus `dev-*` hot-reload targets — discoverable via
   `make help`.
2. Tag compose services with **`infra` / `backend` / `frontend` profiles**, so a single
   `docker-compose.yml` can start the right slice per target instead of multiple files. (Later,
   ADR-0001's `observability` profile slots into the same scheme.)
3. Image builds activate the relevant profiles so all service images are produced.

## Considered options

| Option | Decision | Why |
|---|---|---|
| Makefile + Compose profiles | **Chosen** | Single discoverable interface; subset start-up from one compose file |
| Keep shell scripts | Rejected | Duplicative, undiscoverable, drift-prone across services |
| Multiple compose files (`-f` per slice) | Rejected | More files to keep in sync than profile tags on one file |
| A task runner (Taskfile/Just) | Rejected | `make` is ubiquitous with zero install; no reason to add a tool |

## Consequences

**Positive**
- One documented entry point; `make help` is the source of truth for commands.
- Stack slices (`make infra`, `make backend`, full stack) come from one compose file via profiles;
  new profiles (e.g. `observability`) extend the scheme without new files.

**Negative / costs**
- `make` recipes and Compose profile tags must be kept in step as services are added.
- The `kubernetes/` manifests are **not** wired into this interface and have drifted stale — the K8s
  path remains a separate, unaddressed follow-up.
