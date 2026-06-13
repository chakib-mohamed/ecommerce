# Spec: Functional (Business) Metrics

## Objective

Make the platform's **business behaviour** observable, not just its plumbing. The observability
stack (OTel tracing + Prometheus + Jaeger + Grafana, see `docs/specs/observability.md`) already
gives us auto-instrumented RED/JVM/Kafka signals on every service — but **zero custom metrics**.
The existing *Ecommerce Overview* dashboard shows infra health (request rate, latency, error rate),
which answers *"is the service up and fast?"* but not *"are people buying things?"*.

This spec defines a curated set of **functional KPIs** — orders, revenue distribution, auth
success/failure, catalog changes, pricing/discounts, and event flow — so operators can see the
*business* pulse of the platform alongside its technical health.

**Goals:**
- A small, high-signal set of business meters recorded in the control layer of the 5 Quarkus services.
- Each meter scrapeable by Prometheus at the existing `/q/metrics` endpoints (no new wiring).
- A new provisioned *Business KPIs* Grafana dashboard rendering them (orders/min, order value
  p50/p95, login success vs failure, catalog mutations, discount amounts, featured cache size,
  events consumed/min).

**Out of scope:**
- Gateway metrics — the Spring Cloud Gateway already exports per-route RED metrics; it gets no
  business meters here.
- The dashboard JSON and observability-doc updates (delivered as the final phase of the parent task).
- The per-service implementation and tests (delivered phase-by-phase after this spec is approved).

---

## Decisions

- **Depth — curated, not exhaustive.** A handful of high-value KPIs per service, chosen for
  business meaning, rather than blanket instrumentation of every method.
- **Approach — direct `MeterRegistry` injection in the control layer.** Business dimensions like
  order value and discount amount only exist *inside* the control methods that compute them, so the
  meters live there. An interceptor/annotation approach was rejected: it cannot see those values,
  and it does not fire in the Mockito unit tests that cover the control layer.
- **Layering — control only.** Meters are recorded in `control/` services exclusively, never in
  `boundary/` resources or `repository/` (per `docs/conventions/architecture-conventions.md`).
- **Visualization — a new dashboard.** A new provisioned *Business KPIs* dashboard; the existing
  *Ecommerce Overview* dashboard is left untouched.
- **Scope — the 5 Quarkus services only:** `authenticate`, `products`, `featured-products`,
  `orders`, `price`. All already ship `quarkus-micrometer-registry-prometheus` and expose
  `/q/metrics`; `MeterRegistry` is CDI-injectable with no extra configuration.

---

## Metric catalog

Micrometer meter names use dot-notation; the Prometheus registry lowercases dots to `_`, appends
`_total` to counters, and emits `_count` / `_sum` (+ buckets) for distribution summaries. The names
below are the **Prometheus** names the dashboard will query. Each row's "Recorded in" column names
the exact control method (and failure branch) verified against the current code.

| Service | Prometheus metric | Type | Tags | Recorded in |
|---|---|---|---|---|
| authenticate | `auth_logins_total` | counter | `outcome=success\|failure` | `UserService.authenticateUser` — failure recorded on the `Optional.empty()` branch (see note) |
| authenticate | `auth_registrations_total` | counter | `outcome=success\|failure` | `UserService.addUser` — failure = `DuplicateEmailException` |
| products | `catalog_products_mutations_total` | counter | `op=create\|update\|delete` | `ProductService.saveProduct` / `updateProduct` / `deleteProduct` |
| products | `catalog_categories_mutations_total` | counter | `op=create\|update\|delete` | `CategoryService.saveCategory` / `updateCategory` / `deleteCategory` |
| products | `catalog_promotions_mutations_total` | counter | `op=create\|delete` | `PromotionService.savePromotion` / `deletePromotion` |
| products | `catalog_images_uploaded_total` | counter | — | image-upload branch in `ProductService.saveProduct` / `updateProduct` |
| products | `catalog_price_updates_consumed_total` | counter | — | `ProductService.updatePrice` (Kafka consumer) |
| featured-products | `featured_events_consumed_total` | counter | `type=updated\|deleted` | `KafkaEventConsumer.consumeProductUpdated` / `consumeProductDeleted` |
| featured-products | `featured_cache_size` | gauge | — | bound to `productMongoRepository.count()` |
| orders | `orders_created_total` | counter | — | `OrderService.saveOrder` |
| orders | `order_value_amount` | summary | — | `OrderService.saveOrder` — records `order.getPrice()` (set from the pricing call) |
| orders | `orders_confirmed_total` | counter | — | `OrderService.confirmOrder` |
| orders | `checkouts_total` | counter | `outcome=success\|failure` | `CartService.checkout` — failure = `CartNotFoundException` / `CartEmptyException` |
| price | `pricing_calculations_total` | counter | `outcome=success\|failure` | `PricingService.calculate` — failure = `InvalidOrderException` |
| price | `pricing_discount_amount` | summary | — | `ApplyPromotionsService.applyPromotion` — recorded per applied discount |
| price | `pricing_price_updates_total` | counter | `outcome=success\|failure` | `PriceService.update` — failure = `InvalidPriceException` |

### Notes

- **Distribution summaries** — `order_value_amount` and `pricing_discount_amount` are
  `DistributionSummary` meters configured with `.publishPercentileHistogram()`, so Grafana can
  render p50/p95 via `histogram_quantile` over the emitted buckets.
- **Gauge** — `featured_cache_size` is a `Gauge` registered once (e.g. via
  `Gauge.builder(...).register(registry)`) bound to `productMongoRepository.count()`; it is read on
  scrape, not incremented.
- **Failure semantics differ by service** — record the failure increment *at the point the domain
  signals failure*:
  - **Login** signals failure by returning `Optional.empty()` (no exception thrown — the timing-safe
    path treats unknown-email and bad-password identically). Record `outcome=failure` on that branch
    *before returning*. (This corrects the parent task plan, which assumed a thrown exception.)
  - **Registration, checkout, pricing-calculate, and price-update** signal failure by throwing a
    `FunctionalException` (`DuplicateEmailException`, `CartNotFoundException` / `CartEmptyException`,
    `InvalidOrderException`, `InvalidPriceException`). Record `outcome=failure` *before* the
    exception propagates.

---

## Conventions

- Inject `io.micrometer.core.instrument.MeterRegistry` into the control service using the same
  `@Inject` style as existing collaborators (e.g. `OrderService`).
- Keep meter names and tag keys in a small `control/MetricNames` constants holder (or
  `private static final` fields) per service, so names stay consistent and greppable.
- Counters and summaries are created lazily via `registry.counter(name, tags...)` /
  `registry.summary(name, tags...)`; Micrometer dedupes by name+tags, so repeated calls are cheap
  and idempotent.
- Record metrics **only** in the control layer — never in `boundary/` or `repository/`.
- All meters must respect the project JSON/naming conventions only insofar as they are
  Prometheus-native; no HTTP JSON is involved.

---

## Verification

End-to-end verification (after the implementation phases land):

1. **Unit** — `./mvnw test -pl <service>` asserts counter/summary values against a real
   `SimpleMeterRegistry` (both `outcome` branches covered).
2. **Exposition** — `make up`, exercise flows through the gateway (register + login success and a
   bad-password failure, create/update/delete a product, create + confirm an order, run a cart
   checkout, trigger a price update), then `curl localhost:<port>/q/metrics | grep <prefix>` shows
   the new names with expected tags.
3. **Prometheus** (http://localhost:9090) — query `orders_created_total`,
   `rate(auth_logins_total[5m])`, etc.; series present, Status → Targets all `up`.
4. **Grafana** (http://localhost:3000) — *Ecommerce Business KPIs* panels render data after the
   flows above.
