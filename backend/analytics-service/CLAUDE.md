# analytics-service/CLAUDE.md

Quarkus 3.20.6.2 service. Serves the back-office dashboard's sales figures at `GET /api/analytics`.

Unlike the other services it owns no operational data and accepts no writes over HTTP. It is a
**read model**: a small star-schema warehouse in its own Postgres database, filled by consuming the
platform's business events. See `docs/adr/0010-analytics-service-event-sourced-warehouse.md` for
why, and `docs/specs/analytics-dashboard.md` for what it serves.

## Warehouse

Liquibase creates the schema at startup (`db/changelog/db.changelog-master.xml`). Two tables:

- **`fact_sales_line`** — one row per line of a completed order, with `line_revenue` already
  resolved from the price and discount frozen onto the order. Unique on `(order_id, product_id)`,
  which is what makes redelivery safe.
- **`dim_product`** — the catalog attributes a sale is sliced by. Keyed on the product uuid, the
  same identifier order lines carry.

## Ingestion

Consumes three topics with its own consumer group per topic, reading from the earliest offset:
`order-paid` (fills the fact), `product-updated` / `product-deleted` (maintain the dimension).
Delivery is at-least-once, so every write is idempotent. Poison messages land on `*-dlq`.

**Revenue is money taken, not orders placed.** The fact table is filled from `order-paid`, so an
order confirmed but never paid for contributes nothing. A confirmation is deliberately not counted —
it would report a declined card as revenue. (`order-initiated`, which used to feed this, was deleted
once moving revenue off it left the event with no consumer at all; two tests still name it, guarding
against the bug being reintroduced under that name.) Nothing needs to reverse a counted
sale either: `CANCELLED` is not reachable from `PAID`, so an order is either paid or cancelled and
never both. See `docs/specs/analytics-revenue.md`.

### The dimension records the category a product is *filed under*

Product events carry `categories` — the single category the product is filed under, which is the
subcategory when it has one, and that entry brings its label. Ingestion takes the dimension's
category from there.

Events also carry `category_id`/`subcategory_id`, but only since the producer was fixed to derive
them (PR #17). Anything published before that has both null, so ingestion must not depend on them —
`ProductEventWireFormatTest` ingests a payload captured before the fix to keep that true. Reading
those fields was the original bug: they were null on every event, and every sale landed under
"Uncategorized".

Consequence: the revenue breakdown groups by the filed (leaf) category — "Dining Tables", not
"Dining". The top-level *id* is available now, but no event carries the parent's **label**, so
rolling up would still mean widening the product event or querying the category tree over REST —
both rejected in ADR-0010.

## Operating the warehouse

### A freshly seeded catalog produces no dimension rows

Products inserted by products-service's Liquibase seed never emit `product-updated`, so on a new
environment the dimension is empty and every sale reports under "Uncategorized" until each product
is written through the API at least once. Sales themselves are unaffected — revenue, units and the
monthly series are all correct; only the category split is.

To prime it, touch the products that have sales (a `PUT /api/products` with the product's current
body emits `product-updated`):

```bash
TOKEN=...   # from POST /api/users/authenticate
for UUID in $(curl -s "http://localhost:81/api/products?size=100" -H "Authorization: Bearer $TOKEN" \
              | python3 -c 'import json,sys; [print(p["uuid"]) for p in json.load(sys.stdin)]'); do
  curl -s "http://localhost:81/api/products/$UUID" -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import json,sys; p=json.load(sys.stdin); [p.pop(k,None) for k in ("image","rating","review_count","promotions","categories")]; print(json.dumps(p))' \
    | curl -s -X PUT http://localhost:81/api/products -H 'Content-Type: application/json' \
        -H "Authorization: Bearer $TOKEN" --data-binary @- -o /dev/null
done
```

### Changing ingestion logic does not repair rows already ingested

Offsets are committed, so a redeployed service does not reprocess what it has already read — the
old, wrong rows stay. To rebuild from the streams, stop the service, reset its consumer groups, and
start it again:

```bash
docker compose stop analytics-service
for T in order-paid product-updated product-deleted; do
  docker exec ecommerce-kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 \
    --group "analytics-service-$T" --topic "$T" --reset-offsets --to-earliest --execute
done
docker compose start analytics-service
```

Replay is safe: ingestion is idempotent, so re-reading the same events rewrites the same rows rather
than doubling them. It only recovers what the broker still retains, and it cannot conjure events
that were never published (see the seeded-catalog note above).

### Replaying does not remove a row, so a stale row survives it

Ingestion rewrites and inserts; it never deletes rows no event mentions. A replay therefore
*corrects* rows the stream still covers and *leaves* everything else exactly as it was.

That matters once, concretely: every row written before revenue moved to `order-paid` came from a
confirmation, and no `order-paid` event exists for it. Replaying will not touch those rows, and
afterwards they are indistinguishable from real revenue. **Clear the fact table as part of the
rebuild**, not merely reset the offsets:

```bash
docker compose stop analytics-service
docker exec ecommerce-analytics-postgres-1 psql -U postgres -d analytics \
  -c 'TRUNCATE fact_sales_line;'
# ... reset the consumer groups as above, then start the service
```

`dim_product` does not need clearing — it is keyed by product and every replayed `product-updated`
overwrites its row in place.

Expect the figures to drop, and expect them to be near zero on an environment that predates
`order-paid`: the event was never published before, so there is nothing to backfill. The first
correct figure is the first order paid after the change shipped.

## Local dev

```bash
make dev-analytics    # quarkus:dev on :8086, against localhost:5433
```

Needs `analytics-postgres` (port 5433) and Kafka from `make infra`. The warehouse is a separate
Postgres container from the operational one, so it can be dropped and rebuilt without touching the
catalog.

## Tests

```bash
./mvnw verify -pl analytics-service     # needs Docker (Testcontainers)
./mvnw test -pl analytics-service -DexcludedGroups=integration   # unit only, no Docker
```

On Docker Engine 25+ the Testcontainers version pinned by the Quarkus BOM negotiates an API version
the daemon refuses ("client version 1.32 is too old"), which surfaces as the misleading "Could not
find a valid Docker environment". Start the daemon with `DOCKER_MIN_API_VERSION=1.24`.
