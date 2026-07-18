# ADR-0008: E2E suite drives the built frontend container, not the Vite dev server

- **Status:** Accepted
- **Date:** 2026-07-12
- **Deciders:** CHAKIB Mohamed
- **Related:** `e2e/playwright.config.ts`, `docker-compose.yml`, `docs/specs/e2e-test-suite.md`

## Context

The new e2e suite (`e2e/`) needs a frontend origin to drive a browser against. Two candidates
exist, both already wired up by `docker-compose.yml`/the `Makefile`:

- `http://localhost:81` — the built `ecommerce-front` nginx image, the `frontend` Compose
  profile, what `make front`/`make up` actually run.
- `http://localhost:3000` — the Vite dev server (`make dev-front`), proxying `/api` to a
  gateway run via `make dev-gateway` or `make backend`.

The two behave differently for one thing that matters here: CORS. The gateway's
`SecurityConfig` reads `cors.allowed-origins` from `CorsProperties`, and — as found while fixing
the login-blocking bug this suite exists to catch (see `docs/specs/e2e-test-suite.md`) —
`docker-compose.yml` sets `CORS_ALLOWED_ORIGINS=http://localhost:3000` for the `api-gateway`
service. That covers the Vite dev server origin but not the built frontend's origin
(`http://localhost:81`). A real browser sends an `Origin` header on every POST/PUT/DELETE
(unlike `curl`, and unlike its own GET requests to the same origin), so driving the suite against
`:81` without also allow-listing that origin would 403 every mutating request — login, checkout,
admin writes — the exact failure mode this suite is meant to prevent.

## Decision drivers

- Test against the artifact that's actually deployed (the built nginx image + `nginx.conf`
  reverse proxy), not a dev-only tool.
- Keep orchestration to one `docker compose up` rather than mixing a host-level Vite process with
  a dockerized backend.
- Minimize the blast radius of any production-code change riding along with test infrastructure.

## Decision

Point `e2e/playwright.config.ts`'s `baseURL` at `http://localhost:81`, and extend
`docker-compose.yml`'s `CORS_ALLOWED_ORIGINS` for `api-gateway` to
`http://localhost:3000,http://localhost:81` (Spring binds a comma-separated env value to
`List<String>` natively — a one-line, low-risk change). `:3000` stays allow-listed so
`make dev-front` + `make dev-gateway` local development is unaffected.

## Considered options

| Option | Decision | Why |
|---|---|---|
| `localhost:81` (built image) + extend CORS allow-list | **Chosen** | Tests the real deployment artifact; single `docker compose up`; the CORS change is one line and additive (doesn't remove `:3000`) |
| `localhost:3000` (Vite dev server) | Rejected | No `docker-compose.yml` change needed, but never exercises the built frontend image/`nginx.conf`, and CI would need to run a host-level Vite process alongside the dockerized backend instead of one `docker compose up` |

## Consequences

**Positive**
- The suite exercises the same artifact a deploy would ship, including the nginx reverse-proxy
  config, not just the dev server's Vite proxy.
- Orchestration stays a single `docker compose --profile infra --profile backend --profile
  frontend up -d` (`make e2e-up`).

**Negative / costs**
- One additional origin to keep in sync if `CORS_ALLOWED_ORIGINS` is ever restructured (e.g. moved
  to a real production allow-list per environment) — a future change touching that env var should
  check both `docs/specs/e2e-test-suite.md` and this ADR.
