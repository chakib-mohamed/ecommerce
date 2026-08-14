# E2E test suite

Browser-driven end-to-end tests against the real stack — Postgres/Mongo/Kafka/Redis, all 6
backend services, and the built frontend image — using [Playwright](https://playwright.dev).

See `docs/specs/e2e-test-suite.md` for why this exists and what it covers.

## Prerequisites

- Docker (the stack runs via `docker compose`, same as `make up`/`make front`)
- Node >= 20.19 (`.nvmrc` pins the same version as `frontend/`)

## Running locally

```bash
make e2e            # full cycle: bring up the stack, run the suite, tear down
```

Or step by step, if you want the stack to stay up between runs (e.g. while iterating on a spec):

```bash
make e2e-env         # generate a throwaway .env with JWT keys (idempotent — skips if .env exists)
make e2e-up          # docker compose up, full infra+backend+frontend, waits for health
make e2e-run         # npm ci + playwright install + run the suite
make e2e-down        # tear down when done
```

Running a single spec, headed, against a stack you've already brought up with `make e2e-up`:

```bash
cd e2e
npm ci
npx playwright install --with-deps chromium
npx playwright test --headed --project=chromium specs/checkout.spec.ts
```

## Reports

On failure, Playwright captures a trace, screenshot, and video. After a run:

```bash
npm run report   # opens the HTML report (from e2e/)
```

In CI, the report is uploaded as a `playwright-report` artifact; on failure, `docker compose logs`
output is uploaded too (`compose-logs` artifact) for diagnosing backend-side failures.

## Layout

- `fixtures/test-users.ts` — the two seeded accounts (`backend/dev-scripts/mongodb/03-users.js`)
  **plus a buyer per spec**, registered fresh each run. Anything that places an order gets its own
  account: an order history is mutable shared state and these specs run in parallel. Only
  `auth.spec` (needs a fixed credential pair to type into the login form) and `admin.spec` (needs
  the admin role) use the seeded pair.
- `fixtures/auth.ts` — `loginAs(page, user)` helper, used by `global-setup.ts`
- `fixtures/api.ts` — `waitForApiCall` helper for asserting exact backend requests
- `fixtures/smoke.ts` — blocks until the stack can complete a purchase *and* answer the review
  gate; see `docs/specs/e2e-test-suite.md` for why "healthy" is not "ready"
- `global-setup.ts` — registers this run's spec buyers, logs every account in via the real UI,
  saves Playwright `storageState` to `.auth/*.json` (gitignored), then waits for the stack to be
  able to sell
- `specs/*.spec.ts` — one file per flow; see `docs/specs/e2e-test-suite.md` for the mapping
