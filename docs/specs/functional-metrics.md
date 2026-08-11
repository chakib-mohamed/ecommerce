# Spec: Functional (Business) Metrics

## Objective

Make the platform's **business behaviour** observable, not just its plumbing. The observability
stack (OTel tracing + Prometheus + Jaeger + Grafana, see `docs/specs/observability.md`) already
gives us auto-instrumented RED/JVM/Kafka signals on every service — but **zero custom metrics**.
The existing *Ecommerce Overview* dashboard shows infra health (request rate, latency, error rate),
which answers *"is the service up and fast?"* but not *"are people buying things?"*.

This spec defines a curated set of **functional KPIs** — orders, revenue distribution, auth
success/failure, catalog changes, and pricing/discounts — so operators can see the
*business* pulse of the platform alongside its technical health.

**Goals:**
- A small, high-signal set of business meters recorded in the control layer of the 4 Quarkus services.
- Each meter scrapeable by Prometheus at the existing `/q/metrics` endpoints (no new wiring).
- A new provisioned *Business KPIs* Grafana dashboard rendering them (orders/min, order value
  p50/p95, login success vs failure, catalog mutations, discount amounts).

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
- **Scope — the 4 Quarkus services that own business behaviour:** `authenticate`, `products`,
  `orders`, `price`. All already ship `quarkus-micrometer-registry-prometheus` and expose
  `/q/metrics`; `MeterRegistry` is CDI-injectable with no extra configuration.
- **`featured-products` excluded.** It is a read-model projection: its meters would measure the
  Kafka read-model sync (events consumed, cache size) — system/integration health, not business
  behaviour. The underlying business events are already counted at their source in `products`
  (`catalog_products_mutations_total`), so featured-products gets no functional meters here.

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
| orders | `orders_created_total` | counter | — | `OrderService.saveOrder` |
| orders | `order_value_amount` | summary | — | `OrderService.saveOrder` — records `order.getPrice()` (set from the pricing call) |
| orders | `orders_confirmed_total` | counter | — | `OrderService.confirmOrder` |
| orders | `checkouts_total` | counter | `outcome=success\|failure` | `CartService.checkout` — failure = `CartNotFoundException` / `CartEmptyException` |
| orders | `orders_cancelled_total` | counter | — | `OrderService.cancelOrder`, and `SagaService` on a stock refusal or a failed charge |
| orders | `orders_reserved_total` | counter | — | `SagaService.onStockReserved` |
| orders | `orders_paid_total` | counter | — | `SagaService.onPaymentCaptured` — this, not confirmation, is revenue |
| orders | `orders_sagas_timed_out_total` | counter | — | `SagaDeadlineSweep.abandon` — **alerted on**; each one held stock |
| payment | `payments_captured_total` | counter | — | `PaymentService.record` on the captured branch |
| payment | `payments_declined_total` | counter | — | `PaymentService.record` on the refused branch |
| payment | `payments_gateway_faults_total` | counter | — | `PaymentService.capture` — **alerted on**; the provider gave no usable answer |
| payment | `payments_redelivered_total` | counter | — | `PaymentService.capture` — answered from the local record, charged nothing |
| price | `pricing_calculations_total` | counter | `outcome=success\|failure` | `PricingService.calculate` — failure = `InvalidOrderException` |
| price | `pricing_discount_amount` | summary | — | `ApplyPromotionsService.applyPromotion` — recorded per applied discount |
| price | `pricing_price_updates_total` | counter | `outcome=success\|failure` | `PriceService.update` — failure = `InvalidPriceException` |

### Notes

- **Distribution summaries** — `order_value_amount` and `pricing_discount_amount` are
  `DistributionSummary` meters configured with `.publishPercentileHistogram()`, so Grafana can
  render p50/p95 via `histogram_quantile` over the emitted buckets.
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

## Alerting

Rules live in `observability/rules/*.rules.yml`, mounted into Prometheus and evaluated every 30s.
They are visible on Prometheus's Alerts page and in Grafana.

### Delivery

Prometheus hands firing alerts to **Alertmanager** (`observability/alertmanager.yml`), which decides
what happens to them next. Three things it does that Prometheus alone does not:

- **Grouping** by `alertname` + `job`, so one broken service produces one notification rather than
  one per instance, batched on a 30s wait and a 5m interval.
- **Severity routing.** `severity: critical` takes its own receiver and repeats hourly; everything
  else repeats every four hours. The label on the rule is what selects the route — a rule that sets
  no severity silently takes the default path, which is why every rule above sets one.
- **Inhibition.** A `ServiceNotScraped` suppresses the other alerts for that same `job`, because a
  service that is not being scraped makes every rule reading its metrics meaningless, and the
  symptoms otherwise bury the cause. This matches on `job`, so it covers `SagaStepTimedOut`,
  `PaymentGatewayFault` and `CapturesRepeatedlyRedelivered` — the rules whose expressions keep the
  label. It cannot cover `OrdersConfirmedButNonePaid` or `PaymentsMostlyDeclined`, which aggregate
  with `sum()` and drop every label including `job`. That is a property of those expressions.

**Where alerts go is still parametrized, and that is the remaining decision.** The default receiver
posts the full payload to a local `alert-sink` container, so the path is verifiable —
`docker compose logs alert-sink` shows exactly what was delivered. That default exists because a
receiver that quietly discards its alerts is indistinguishable from a working one, which was the
failure this replaced.

Pointing them at a real destination is one receiver block in `observability/alertmanager.yml`; the
Slack and email forms are written out there, commented, ready to uncomment. Both read their
credential from a file mounted at `/etc/alertmanager/secrets/` rather than an inline value — a
webhook URL or SMTP password committed to this repository is a credential handed to everyone who
can read it.

Both halves are validated in CI by the `observability-config` job: `amtool check-config` parses the
Alertmanager config, and a cross-check asserts that the Alertmanager `prometheus.yml` points at is
actually a service in `docker-compose.yml`. Neither tool checks that seam on its own, and a
misspelled hostname there produces a stack where every rule evaluates, the Alerts page looks
healthy, and nothing is ever delivered.

| Alert | Fires on | Why it is worth waking up for |
|---|---|---|
| `SagaStepTimedOut` | any `orders_sagas_timed_out_total` increase | Each abandoned step held stock out of sale, and the buyer was told their order was cancelled for a timeout that was nobody's fault |
| `OrdersConfirmedButNonePaid` | confirmations flowing, payments at zero for 15m | The saga is broken past `RESERVED`; every order is reserving stock and none complete |
| `PaymentGatewayFault` | any `payments_gateway_faults_total` increase | The worst case in `payment.md` §8 — the money may have moved and the order was cancelled anyway. Only reconciliation recovers it |
| `PaymentsMostlyDeclined` | >80% declines over 15m, min 10 declines | A wrong or expired provider key looks exactly like this |
| `CapturesRepeatedlyRedelivered` | >5 redeliveries in 15m | The charge is safe, but a reply is not getting through and orders are waiting |
| `ServiceNotScraped` | `up == 0` for 5m | Every alert above is silent while its service is unscraped, and silence reads as health |

Two rules deliberately do **not** alert on volume being unusual. A busy day and a broken provider
must not look the same, or the alerts get muted — and a muted alert protects nothing.

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
