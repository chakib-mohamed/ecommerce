# E2E test suite

**Status:** Proposed.

## Why

This branch (`feature/cloud-shop-redesign`) was verified by hand in a prior session — bringing up
the full Docker Compose stack and driving a headless browser through the real user flows. That
manual pass caught a real, previously-invisible bug: `docker-compose.yml`'s `api-gateway` service
never configured `CORS_ALLOWED_ORIGINS` / `CORS_ALLOW_CREDENTIALS`, so `cors.allowed-origins`
defaulted to empty. Every POST/PUT/DELETE from a real browser (which sends an `Origin` header,
unlike `curl`, and unlike GET requests to the same origin) was CORS-rejected with 403 —
regardless of path permissions. This was invisible to GET-only manual spot checks and to
curl-based testing. Confirmed as the root cause (not just a contributing factor) by reverting the
fix in isolation and watching `e2e/specs/auth.spec.ts` — and in fact `global-setup.ts`'s own
pre-authentication — fail red; see that spec's docstring for the isolation test and a note on a
second change (an explicit `permitAll()` for the login path) that rode along in the same commit
but wasn't proven necessary on its own.

This bug was not caught by existing CI (`frontend` job only lints and builds; `backend` job runs
per-service backend tests, which don't exercise the gateway's browser-facing behavior end to end).
This suite makes that class of regression — silent cross-service integration breakage that only
shows up when a real browser drives the real stack — catchable automatically, not just when
someone happens to click through the app by hand.

## Scope

One spec file per flow, all under `e2e/specs/`:

| Spec | Covers |
|---|---|
| `catalog.spec.ts` | `/browse` renders real products; category filter fires a server-side filtered request (`GET /api/products?...&category_id=`); load-more appends the next page and terminates once a short page returns. This is the branch's HEAD-commit feature. |
| `auth.spec.ts` | Login (`POST /api/users/authenticate`) succeeds with seeded credentials; invalid credentials are rejected cleanly. Explicitly asserts a `200`, not a `403` — the direct regression check for the two bugs above. |
| `checkout.spec.ts` | Add to cart → fill checkout form → place order (`POST /api/orders`) → land on `/confirm` with a real order id. |
| `orders.spec.ts` | A logged-in buyer sees their own previously-placed order(s), and the order-search request (`POST /api/orders/search`) is scoped to their email — the "own orders by authenticated user" fix (commit `f160317`). |
| `admin.spec.ts` | Admin back-office (`/admin/products`, `/admin/categories`) renders real data; one real write (create + delete a throwaway subcategory) proves add/edit/delete actually work. |
| `order-lifecycle.spec.ts` | An order placed through the UI reaches `PAID` across four services and a broker; and a confirm whose answer is lost does not place a second order. The only tests that fail when the saga chain breaks rather than one of its links. |
| `reviews.spec.ts` | Verified-purchaser reviews: a buyer with a *paid* order can review, a buyer with none gets `403`, and a review can be submitted then deleted. |

### Out of scope

- Cross-browser coverage (Firefox/WebKit) — one browser (Chromium) is enough to catch the class of
  backend-integration bugs this suite targets; add projects to `playwright.config.ts` if
  cross-browser regressions start to matter.
- Visual regression testing.
- Load/performance testing.
- Exhaustive admin CRUD coverage (every field, every validation error) — this suite proves the
  wiring works end to end, not full UI test coverage. Component-level/unit testing (if added later)
  is the right place for exhaustive input validation.

## How it runs

```bash
make e2e   # build, bring up the full stack, run the suite, tear down
```

See `e2e/README.md` for day-to-day commands (running a single spec, headed mode, viewing the HTML
report) and `scripts/gen-jwt-keys.sh` / `make e2e-env` for the JWT keypair the stack needs to boot
at all (a real, pre-existing onboarding gap this suite's setup also closes).

CI runs the suite on push to `main` and on pull requests carrying an `e2e` label — see
`docs/adr/0008-e2e-target-localhost-81.md`'s sibling note in `.github/workflows/ci.yml` for why it
isn't on every PR by default.

### One buyer per spec

Every spec that places an order gets its **own account, registered fresh for that run**
(`e2e/fixtures/test-users.ts`). Only `auth.spec` and `admin.spec` use the two seeded accounts —
the first needs a fixed credential pair to type into the login form, the second needs the admin
role.

Sharing one buyer across the suite caused three separate failures, none of them obvious:

1. **Assertions on a moving target.** The history pages at five. Six specs placing orders for one
   account meant the order under test could be on any page, depending on which spec finished
   first — so a test asserting it was on page one passed only while the suite was small.
2. **A test passing because another spec did its setup.** When purchase verification was tightened
   to require `PAID`, `reviews.spec` still stopped at `INITIATED` and kept passing, because
   `order-lifecycle` drove the same product to `PAID` for the same buyer in parallel. Green,
   asserting nothing about its own setup, and it would have failed run on its own.
3. **Concurrent sagas on one account**, contending on the same rows and the same order search.

Emails carry a per-run suffix, so a stack left up between runs never hands the next run a history
full of the last one's orders — which would reintroduce the same problem one run later.

### Waiting for ready, not for healthy

`global-setup.ts` logs each role in, and then **blocks until the stack can actually complete a
purchase** — place an order, confirm it, and see it reach `PAID` — retrying for up to three minutes
before giving up (`e2e/fixtures/smoke.ts`).

This exists because `docker compose up --wait` returns when every health endpoint answers, which
happens well before a service's REST clients, Kafka consumers and database pools are usable. The
suite used to start at exactly that moment and immediately drive the heaviest path in the system:
`confirm` re-checks prices against products-service and price-service over HTTP before committing,
so it is the first operation needing three services to be genuinely working rather than merely
answering. The symptom was `confirm` returning **500** on a first attempt and passing on retry —
three of fifteen tests flaking, and once a hard failure.

The gate **absorbs** that window rather than detecting it: the errors it swallows are the ones the
specs would otherwise hit. It asserts nothing about behaviour and is not a test. If it cannot
complete a purchase within the deadline, every order, checkout and review spec was going to fail
anyway, and failing here says so once instead of fifteen times.

Its own logic is verified against a stub that fails `confirm` with 500 a fixed number of times: it
retries through a recovering stack, gives up with a readable message on one that never recovers,
and returns immediately on a healthy one.

## Assertion philosophy

Contributors extending this suite should follow the same judgment calls made when it was written,
rather than re-litigating them per PR:

- **Assert real API calls and DOM state.** Prefer `page.waitForResponse` (via `fixtures/api.ts`'s
  `waitForApiCall`) to assert the exact request the app made (URL, query params, status code) over
  asserting DOM state alone — DOM state can be right for the wrong reason (e.g. stale cached data).
- **Don't hardcode seed counts or ids.** The seed catalog has a fixed size today, but that's an
  artifact of `db.changelog`/`mongo-init`, not a contract. Resolve category ids/names from API
  responses at test time (see `catalog.spec.ts`) instead of hardcoding "Dining is id 10".
- **Mock only where a real assertion would be flaky by construction.** The one exception is
  `catalog.spec.ts`'s load-more test: the seed catalog happens to be exactly one page today, so
  there's no *real* second page to assert an append against. That test mocks the second page's
  response to a short, non-empty page — real first page, mocked second page — rather than either
  hardcoding today's exact count or skipping the append assertion entirely.
- **Clean up writes.** Any spec that creates data (`checkout.spec.ts`'s order, `admin.spec.ts`'s
  throwaway subcategory) either leaves genuinely expected persistent evidence (an order — that's
  what order history is for) or deletes what it created in the same test (the subcategory) so
  shared seed data doesn't drift across runs.
