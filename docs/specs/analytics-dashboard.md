# Back-office Analytics Dashboard

**Status:** Proposed — decisions resolved, OpenAPI contract not yet written.

## Why

The admin back-office **Dashboard** (`/admin`) is fully built on the frontend, but every
sales/revenue/units figure it shows is **fabricated client-side**. `frontend/src/lib/analytics.ts`
synthesises months of sales, per-product units, revenue, and a category breakdown with a seeded PRNG
from the product catalog — its own header comment says the backend has no sales/analytics endpoint
yet and that a follow-up should swap these for a real `/api` call. Only the catalog and the
low-stock panel are real today.

There is no backend analytics of any kind — no service, endpoint, DTO, or route. (The
Prometheus/Grafana *business KPIs* from `functional-metrics.md` are operator observability piped to
Grafana, not a customer-facing API, and cannot feed this dashboard.)

## Goal

Serve the admin Dashboard real numbers — monthly revenue over the last 12 months, per-product units
and revenue, a revenue-by-category breakdown, and total revenue — from a purpose-built analytics
store fed by the platform's existing business events.

## Decisions

- **A new, dedicated `analytics-service`.** Analytics is a read-optimised concern with a different
  data shape (aggregations over history) than any operational service. It gets its own service
  rather than bolting a reporting endpoint onto orders-service, keeping OLTP and reporting
  concerns — and their load — separate. Reachable at `/api/analytics` through the gateway.
- **Backed by its own data warehouse (PostgreSQL star schema).** The service owns a separate
  `analytics` database modelled as a small star schema (a sales **fact** table plus a product
  **dimension**), rather than querying operational databases live. Queries are simple `GROUP BY`
  rollups over pre-shaped rows, so the dashboard stays fast and never couples to orders/products
  availability at request time.
- **Populated by consuming the platform's business events.** The warehouse is filled by subscribing
  to the events other services already publish, with the analytics-service's own consumer group and
  idempotent writes. No batch ETL, no live cross-service calls. Delivery is at-least-once, so
  ingestion de-duplicates on the source record's identity.
- **Only completed (confirmed) orders count as sales.** This is the natural and only option — an
  order is evented only when it is confirmed, so pending/abandoned orders never reach the warehouse
  and never affect any figure.
- **Line revenue is computed from the order snapshot** frozen at purchase time
  (`units × unit_price × (1 − discount%)`), so figures stay stable even if a product's live price
  later changes.
- **Category breakdown comes from the product dimension.** Orders carry no category, but product
  events do; the warehouse maintains a product→category dimension from that stream and joins the
  sales fact to it. A product that can't be resolved (e.g. deleted before it was ever seen) folds
  into an **"Uncategorized"** bucket rather than failing the request.
- **12-month rolling window ending in the current month**, always 12 points (months with no sales
  return `0`), labelled with short month names to match the frontend `SalesChart`.
- **Read-only, admin-facing, authenticated** (`@Authenticated`).

## Architecture

- **Ingestion (`control` layer).** Kafka consumers subscribe to the three relevant streams: the
  order-completion stream (order total, line items with product id / quantity / unit price /
  discount, buyer, timestamp) fills the sales **fact**; the product create/update and product-delete
  streams maintain the product **dimension** (title + category/subcategory labels). Consumers use a
  new consumer group reading from the earliest offset (so the warehouse backfills history on first
  run), idempotent upserts keyed on the source record id, and a dead-letter-queue failure strategy —
  mirroring the existing consumer pattern in `featured-products-service`
  (`.../control/KafkaEventConsumer.java` + its `application.properties`).
- **Warehouse (`entity` + `repository` + Liquibase).**
  - `dim_product` — product identifier (natural key, matching what order lines reference), `title`,
    `category_id`, `category_label`, `subcategory_id`, `subcategory_label`, `deleted` flag.
  - `fact_sales_line` — one row per line of a completed order: `order_id`, `product_id`,
    `product_title` (snapshot), `units`, `unit_price`, `percentage_off`, `line_revenue` (persisted,
    computed at ingest), `order_date`, `order_month` (rollup bucket), `user_id`. Unique
    `(order_id, product_id)` makes re-delivery of the same order a no-op.
  - Schema created via Liquibase (`db.changelog-master.xml` + a `001-init-schema.sql` changeset);
    Panache repositories hold the aggregation queries.
- **Query (`control` + `boundary`).** `AnalyticsService` runs four warehouse rollups and assembles
  the response; `AnalyticsResource` exposes `GET /analytics`. JSON is snake_case / null-omitted /
  ISO-8601 via a `CustomJsonbConfigCustomizer` (copied from products-service).
- **Layering** follows the BCE convention (`boundary`/`control`/`entity`/`repository`) enforced by
  the shared ArchUnit suites, which the new service copies and re-packages.
- **Verify during implementation** which product identifier order lines carry (`ProductVO.productID`)
  versus what the product stream is keyed on (product `uuid`), so the fact→dimension join key is
  correct.

## Sketch

`GET /analytics` → `AnalyticsResponse`:

- `sales` — 12 × `{ month, value }`, oldest→newest, revenue per month (0 where none).
- `product_sales` — `{ product_id, name, units, revenue }`, ranked by units descending.
- `category_breakdown` — `{ id, name, value, pct }`, ranked by revenue descending; unresolved
  products fold into "Uncategorized".
- `total_revenue` — sum of all confirmed line revenue.

The frontend keeps its existing `Analytics` / `MonthSale` / `ProductSale` / `CategoryRevenue` TS
types and its chart components unchanged; only the data source swaps from `buildAnalytics()` to this
endpoint (product/category counts and low-stock stay on the catalog the admin UI already loads).

## Workflow

Follows the standard repo gate sequence per `CLAUDE.md`: spec (this document) → **OpenAPI (next step
— needs approval before any code)** → failing tests (approval) → implementation → infra wiring
(new Maven module, Docker Compose service + `analytics` database, gateway route, Makefile) →
frontend rewire. All payloads obey the JSON serialization conventions (snake_case, null-omission,
ISO-8601).

## Until this ships

The frontend keeps its deterministic `buildAnalytics()` fallback so the Dashboard stays stable and
renders consistent numbers.
