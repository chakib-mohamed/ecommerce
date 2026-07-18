# ADR-0009: Playwright for end-to-end browser testing, gated in CI

- **Status:** Accepted
- **Date:** 2026-07-18
- **Deciders:** CHAKIB Mohamed
- **Related:** `e2e/`, `Makefile`, `.github/workflows/ci.yml`, `docs/specs/e2e-test-suite.md`,
  ADR-0008 (test-target origin, a narrower follow-on decision)

## Context

This repo had no end-to-end or frontend test tooling at all — no Playwright/Cypress/Jest/Vitest,
no test files, nothing beyond `frontend`'s lint+build CI job. That gap let a real bug ship
undetected: `docker-compose.yml`'s `api-gateway` never configured `CORS_ALLOWED_ORIGINS`, so every
POST/PUT/DELETE from a real browser was silently CORS-rejected with 403 — invisible to GET-only
manual checks, curl-based testing, and the existing CI (backend tests exercise services directly,
not through the gateway from a browser; frontend CI only lints and builds). It was only caught by
manually driving a browser against the full stack — see `docs/specs/e2e-test-suite.md`.

Four sub-decisions had to be made to close this gap with a permanent, repeatable suite:

1. Which framework.
2. Where the suite's code lives relative to `backend/`/`frontend/`.
3. Whether/how it runs in CI, given it needs the full stack up (6 JVM services +
   Postgres/Mongo/Kafka/Redis/Zookeeper + frontend), unlike the fast existing jobs.
4. Which frontend origin to drive against — split out as ADR-0008 since it has its own
   independent trade-off (and its own one-line production-config change).

## Decision drivers

- Catch backend-integration regressions (auth, CORS, cross-service wiring) that only manifest
  through a real browser hitting the real stack — not achievable by unit tests or by testing
  services in isolation.
- Fit the repo's existing conventions rather than inventing new ones: `docs/` layout,
  `Makefile`/Compose-profile orchestration, ADRs for infra decisions.
- Keep the fast PR feedback loop (`frontend` lint+build, `backend` per-service `mvnw verify`)
  fast — don't make every PR pay for a multi-minute full-stack bring-up.

## Decision

1. **Framework: Playwright Test (`@playwright/test`).** TypeScript-native (matches the
   frontend's stack), first-class network assertions (`page.waitForResponse` — needed to assert
   exact request shapes like `GET /api/products?...&category_id=`), and built-in
   trace/video/screenshot capture on failure, which matters for a suite driving a real
   multi-service backend rather than mocks. Chromium only for now (see
   `docs/specs/e2e-test-suite.md`'s scope note) — add projects to `playwright.config.ts` if
   cross-browser regressions start to matter.

2. **Standalone top-level `e2e/` workspace**, not nested under `frontend/`. These tests exercise
   the whole system, not the frontend in isolation — the same reasoning the repo already applies
   by giving `backend/`/`frontend/` separate toolchains. Also keeps Playwright's dependencies out
   of `frontend/package.json`'s `--legacy-peer-deps` situation (`react-idle-timer@4.3.6` vs
   React 18).

3. **CI: a 4th job, gated — not on every PR.** Fires on push to `main` (post-merge signal,
   same-commit bisectability) plus an opt-in `e2e` PR label for PRs that specifically touch
   cross-service flows (auth, checkout, gateway/CORS config). Full-stack bring-up realistically
   adds several minutes beyond the existing jobs; that cost isn't worth paying on every PR
   regardless of what changed, but the suite still needs to run somewhere before `main` moves
   forward untested.

4. Orchestration reuses `docker compose --profile infra --profile backend --profile frontend`
   (matching `make front`/`make up`) via new `make e2e*` targets, rather than a bespoke test
   harness — see `Makefile`.

## Considered options

| Decision | Option | Outcome | Why |
|---|---|---|---|
| Framework | Playwright | **Chosen** | TS-native, strong network assertions, trace capture |
| Framework | Cypress | Rejected | Comparable network layer (`cy.intercept`), but weaker multi-origin/multi-tab support and no built-in trace viewer as useful for debugging a flaky full-stack CI run |
| Workspace layout | Standalone `e2e/` | **Chosen** | Exercises the whole system; avoids frontend's peer-dep/ESLint entanglement |
| Workspace layout | `frontend/e2e/` | Rejected | Cleaner single-workspace story, but couples unrelated tooling and dependency ranges |
| CI trigger | Push to `main` + opt-in PR label | **Chosen** | Keeps PR feedback fast; still gates `main` |
| CI trigger | Every PR | Rejected | Maximum safety net, but several extra minutes on every PR regardless of relevance |
| CI trigger | Nightly/scheduled only | Rejected | Decouples the signal from the commit that caused it — harder to bisect |

## Consequences

**Positive**
- This exact class of bug (silent cross-service/CORS breakage invisible to unit tests and to
  GET-only checks) is now caught automatically, not just when someone happens to click through
  the app by hand.
- `make e2e` gives a single, discoverable local reproduction of what CI runs, consistent with
  ADR-0006's Make + Compose-profile interface.

**Negative / costs**
- A 4th toolchain (`e2e/package.json`) to keep dependencies current in, alongside `backend/`'s
  Maven modules and `frontend/`'s npm workspace.
- Full-stack CI runs are slower and more resource-intensive than the existing jobs; the
  push-to-`main` + label-gating in decision #3 is a direct mitigation, not a full solution — a
  regression introduced and merged without the `e2e` label attached still surfaces only after
  merge, not before.
- The suite depends on Docker being available in CI and locally, same as `backend`'s
  Testcontainers-based integration tests already do.
