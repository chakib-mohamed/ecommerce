# ADR-0010: Analytics as a dedicated service over an event-sourced read-model warehouse

- **Status:** Proposed
- **Date:** 2026-07-23
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/specs/analytics-dashboard.md`, `docs/adr/0002-transactional-outbox-and-mongo-replica-set.md`, `docs/conventions/architecture-conventions.md`

## Context

The admin back-office **Dashboard** (`/admin`) renders sales trends, best-sellers, a revenue-by-
category breakdown, and headline KPIs — but every number is **fabricated on the client** by a seeded
PRNG (`frontend/src/lib/analytics.ts`). There is no backend analytics of any kind: no service, no
endpoint, no data. The dashboard's own header comment marks the gap and asks for a real `/api`
source.

Serving these figures for real is a fundamentally different workload from anything the platform does
today. The six existing services are **OLTP**: they answer point queries and single-aggregate
writes. Analytics is **OLAP**: rollups over the *whole history* of orders, sliced by month, product,
and category. The source data is also **split across services** — revenue and line items live in
orders-service (Mongo), while the product→category mapping needed for the category breakdown lives
only in products-service (Postgres). Orders carry no category at all.

The platform already emits the business events this needs, through the transactional outbox adopted
in **ADR-0002**: `order-initiated` (published *on confirmation*, carrying order total, line items
with product id / qty / unit price / discount, buyer, timestamp) and `product-updated` /
`product-deleted` (carrying the product's category labels). These streams are at-least-once.

## Decision drivers

- **Keep OLAP load off the OLTP stores.** History-spanning `GROUP BY` scans should not compete with
  checkout/catalog traffic on the operational databases.
- **Fast, self-contained reads.** The dashboard should not fan out live calls to two services (and
  N per-product category lookups) on every load, nor couple its availability to theirs.
- **Cross-service join without cross-service coupling.** Revenue (orders) must be joined to category
  (products) somewhere that owns neither operational schema.
- **Reuse existing infrastructure.** Prefer the event streams and the Postgres/Panache/Liquibase
  stack already in the repo over introducing new moving parts.
- **Honor the platform's conventions** — BCE layering, the ArchUnit suites, the 100% branch-coverage
  gate, snake_case JSON, blocking JAX-RS + synchronous Panache (no reactive outside the gateway).

## Decision

Stand up a **new, dedicated `analytics-service`** (the 6th Quarkus service) that owns a **PostgreSQL
star-schema data warehouse** as a **read model**, populated **asynchronously by consuming the
existing Kafka business events** — a CQRS-style read side layered on the ADR-0002 outbox. Concretely:

1. **Separate service, separate database.** `analytics-service` exposes `GET /api/analytics` through
   the gateway and owns an isolated `analytics` Postgres database. It never reads another service's
   operational store or calls another service at query time.

2. **Star schema shaped for the dashboard.** A `fact_sales_line` table (one row per line of a
   confirmed order, with `line_revenue` computed and persisted at ingest, a `YYYY-MM` rollup bucket,
   and a unique `(order_id, product_id)` key) plus a `dim_product` dimension (title + category /
   subcategory labels, soft-delete flag). Every dashboard panel becomes a simple `GROUP BY` rollup.

3. **Event-sourced ingestion with idempotent upserts.** Kafka consumers with the service's own
   consumer group read from the **earliest** offset (so the warehouse backfills history on first
   run): `order-initiated` fills the fact; `product-updated` / `product-deleted` maintain the
   dimension. Delivery is at-least-once, so writes de-duplicate on the source record's identity
   (order id / product uuid). Consumers reuse the **DLQ + idempotency** pattern from ADR-0002
   (`failure-strategy=dead-letter-queue`, `fail-on-deserialization-failure=false`), mirroring
   `featured-products-service`.

4. **"Completed sales only" for free.** Because an order is evented only on confirmation, INITIATED /
   abandoned orders never reach the warehouse — no status filter is needed and no pending revenue
   can leak in.

5. **Category resolved inside the warehouse.** The fact→`dim_product` join yields revenue-by-
   category with no runtime call to products-service; products that never resolve fold into an
   "Uncategorized" bucket.

## Considered options

| Area | Chosen | Rejected | Why rejected |
|---|---|---|---|
| Placement | New dedicated `analytics-service` | Add a reporting endpoint to orders-service | Puts OLAP scans on the checkout store; still can't resolve category (lives in products-service); mixes read-model concerns into an OLTP aggregate |
| Read store | PostgreSQL star-schema read model | Query operational DBs live and aggregate in-app per request | Couples dashboard latency/availability to two services + N category lookups; repeats heavy scans every load; cross-store join has no natural home |
| Ingestion | Consume existing Kafka events (event-sourced) | Scheduled batch ETL pulling from operational DBs/APIs | Reaches into other services' stores, adds staleness windows, and ignores the event streams that already exist |
| Change capture | Reuse the ADR-0002 outbox topics | Debezium CDC onto the operational tables | New connector infra to operate; the outbox already publishes exactly the business events needed |
| Warehouse tech | Postgres (Panache + Liquibase) | Dedicated OLAP engine (ClickHouse/DuckDB) | New technology with no Panache/Liquibase/ArchUnit fit and extra infra; dataset scale doesn't justify a columnar engine |
| Delivery guarantee | At-least-once + idempotent upserts | Exactly-once | EOS is far costlier; unique-key upserts already absorb duplicates (same rationale as ADR-0002) |

## Consequences

**Positive**
- OLAP scans are isolated on their own service and database; operational stores are untouched.
- Dashboard reads are a single call to pre-shaped rows — fast, and independent of orders/products
  availability at request time.
- The awkward revenue↔category join lives in one place that owns neither operational schema.
- No new infrastructure category: reuses Kafka, the outbox topics, and the Postgres/Panache/Liquibase
  stack; the new service inherits the existing BCE + ArchUnit + coverage conventions.
- Naturally correct "confirmed-only" semantics, since only confirmed orders are evented.

**Negative / costs**
- **Eventual consistency.** The dashboard trails live orders by the outbox poll + consume lag; it is
  a read model, not a transactional view.
- **A new service to operate** — its own container, database, gateway route, migrations, and consumer
  group / DLQ topics.
- **Backfill depends on retained history.** First-run backfill only sees events still on the topics;
  data older than Kafka retention is not reconstructed (acceptable for a dashboard; a replay/seed job
  is a separate future decision).
- **Dimension fidelity is bounded by the product stream.** Category/promotion *entity* renames aren't
  evented (REST-only today), so the dimension reflects labels as snapshotted onto `product-updated`
  events. Also `order-initiated`'s `creation_date` is a zoneless `LocalDateTime`, treated as
  wall-clock time for month bucketing.
