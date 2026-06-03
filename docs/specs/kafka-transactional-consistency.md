# Spec: Kafka Transactional Consistency (Transactional Outbox)

**Status:** Draft — design spec for review (Spec gate, step 1 of the workflow).
**Scope of this spec:** analysis + target design. No code changes yet.
**Reference implementation:** `products-service` (PostgreSQL / JPA).

---

## 1. Problem statement

Every Kafka **producer** in the platform writes to its database and publishes to Kafka as
two separate, uncoordinated operations — the classic **dual-write problem**. There is no
atomic boundary spanning "business state changed" and "event published", so any failure
between the two leaves the two stores permanently inconsistent: the DB advances but the
event is silently lost, and downstream read models never converge.

### Current behavior per producer

| Service | DB | Publish mechanism | Failure window |
|---|---|---|---|
| **products-service** | Postgres/JPA | CDI `@Observes(during = TransactionPhase.AFTER_SUCCESS)` → `Emitter.send()` (`KafkaEventPublisher.java`) | Publish runs *after* commit. Process crash between commit and send, or a broker outage at send time, loses the event. Nothing records that the event was owed. |
| **price-service** | Mongo | Mongo write, then `emitter.send()` with the returned `CompletionStage` **discarded** | Documented in `docs/reviews/price-service-review.md` #11 (send result swallowed) and #12 (publish not atomic with the Mongo write). Crash or broker rejection = silent loss. |
| **orders-service** | Mongo | `emitter.send(orderDTO)` directly from the resource (`OrdersResource`), not gated on commit | Pure fire-and-forget; even weaker than the AFTER_SUCCESS variants. |

The `AFTER_SUCCESS` pattern in products-service is the *best* of the three — it at least
guarantees the event is only published if the transaction committed (no phantom events) —
but it still cannot guarantee the converse: **a committed write may never produce its
event.**

### Why this is the right time

The project's own `docs/conventions/persistence-conventions.md` (§ "No I/O inside a
transaction") **already mandates** the fix:

> For services that write to both a database and Kafka (e.g. `products-service`), use the
> transactional outbox pattern: persist the event record inside the transaction, publish to
> Kafka only after the commit succeeds.

This spec specifies how to actually implement that convention, starting with products-service.

### Consumers (context — mostly healthy)

`featured-products-service` and the products-service `price-changed` consumer apply
**idempotent UUID upserts**, so redelivery is safe. But both run **auto-ack with no
`failure-strategy` and no dead-letter queue** — a poison (un-processable) message is
redelivered forever, blocking the partition. That is the one consumer-side gap this effort
closes.

---

## 2. Goals / non-goals

**Goals**
- No lost events: once a business write commits, its event is **guaranteed to be published
  at least once** to the broker.
- Bounded eventual consistency between services (lag = poll interval + relay time, not
  "forever" on a crash).
- Poison-message isolation on consumers (DLQ instead of infinite retry).
- Stay within current infrastructure — no new infra components.

**Non-goals**
- Exactly-once delivery. The target is **at-least-once**; consumer idempotency absorbs
  duplicates.
- Multi-region / cross-cluster durability.
- Increasing Kafka broker count or replication factor. The dev infra runs a **single broker,
  replication-factor 1** — a real durability limit, but a separate infra decision, not part
  of this spec. (Called out again in §8.)
- Implementing the outbox in the Mongo producers now — those are design-noted in §6 only.

---

## 3. Target architecture — transactional outbox (products-service)

Replace the post-commit CDI publish with a two-stage outbox: **(a)** the business write and
an outbox row are committed in the **same** JTA transaction; **(b)** a separate scheduled
relay reads unpublished rows and emits them to Kafka, marking each published on broker ack.

```
write request ──▶ @Transactional { business write + INSERT outbox row }  (atomic commit)
                                                   │
                          (separate thread)        ▼
        @Scheduled relay ── SELECT unpublished ──▶ Emitter.send() ──▶ Kafka
                                   ▲                                    │
                                   └──── UPDATE published_at ◀──────────┘ (on ack)
```

### 3.1 Outbox table

New Liquibase changeset `005-add-outbox.sql`, registered in
`db/changelog/db.changelog-master.xml` (sibling of the existing `changes/001-init-schema.sql`
… `004-rich-dev-data.sql`).

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK | event id (also the Kafka idempotency/dedup handle for consumers) |
| `aggregate_type` | `varchar` | e.g. `product` |
| `aggregate_id` | `uuid` | the product uuid — **used as the Kafka message key** for per-aggregate ordering |
| `event_type` | `varchar` | logical event, e.g. `product-updated` / `product-deleted` |
| `topic` | `varchar` | destination topic name |
| `payload` | `text` (or `jsonb`) | JSON-B-serialized event body (camelCase — see §3.4) |
| `created_at` | `timestamptz` | set on insert; relay orders by this |
| `published_at` | `timestamptz` NULL | NULL until the broker acks; set on success |

Partial index for the relay's hot query: `CREATE INDEX ... ON outbox (created_at) WHERE
published_at IS NULL;`

### 3.2 Write path (inside the existing transaction)

The outbox insert goes **inside** the existing `@Transactional` control methods in
`ProductService.java`, alongside the business write — so the row commits atomically with it:

- `persistProduct(...)` (create) and `mergeProduct(...)` (update) → insert a
  `product-updated` outbox row carrying the full `ProductDto`.
- `deleteProductRecord(...)` (delete) → insert a `product-deleted` outbox row carrying the
  product uuid.

This **replaces** the current publish path:
- The `@Observes(AFTER_SUCCESS)` methods in `KafkaEventPublisher.java` are removed (or the
  class is repurposed into the relay).
- The `Event<ProductDeletedEvent>` / `productDeletedEvent.fire(...)` CDI firing and the
  `ProductUpdatedEvent` `fire(...)` in `ProductsResource` are removed.

This keeps the binding convention intact: **no network I/O inside the transaction** — the
transaction only touches Postgres; the actual Kafka `send()` happens later in the relay,
outside any transaction.

### 3.3 Relay path (scheduled publisher)

A new control-layer class (e.g. `OutboxRelay`) — mirroring the injection style of the
current `KafkaEventPublisher` (`@Channel` + `Emitter`) — runs on a Quarkus `@Scheduled`
interval and:

1. Selects a batch of unpublished rows ordered by `created_at`, using
   `SELECT ... FOR UPDATE SKIP LOCKED` so multiple service instances never publish the same
   row twice.
2. For each row, emits to its `topic` via the matching `Emitter`, with the Kafka **message
   key = `aggregate_id`** (guarantees per-product ordering within a partition).
3. On broker ack, stamps `published_at` (a short separate transaction per batch/row).
4. Leaves un-acked rows untouched → naturally retried on the next tick.

Documented parameters: **poll interval** (start ~1s, tunable), **batch size** (bounded, e.g.
100), and the **retention/cleanup** of published rows (see §8).

> Note: because the relay can re-send a row whose ack was lost before `published_at` was
> stamped, delivery is **at-least-once** — which is exactly why consumer idempotency (§5) is
> required, and already present.

### 3.4 Payload & serialization (unchanged conventions)

- Reuse the existing event classes in `products-api`
  (`control/events/ProductUpdatedEvent`, `ProductDeletedEvent`) as the serialized payload
  shape — no new DTOs.
- Payloads stay **camelCase**. Per `docs/conventions/json-serialization-conventions.md`,
  JSON-B Kafka payloads are independent of the HTTP snake_case strategy.
- Keep `io.quarkus.kafka.client.serialization.JsonbSerializer` on the outgoing channels —
  never the Kafka library's `JsonSerializer` (architecture convention).

---

## 4. Producer Kafka hardening

So the relay's `send()` is itself durable and broker-dedup-safe, set on the outgoing
channels in `application.properties`:

| Setting | Value | Why |
|---|---|---|
| `acks` | `all` | broker confirms the write is replicated before ack (as far as RF allows) |
| `enable.idempotence` | `true` | broker drops producer-side duplicate sends |
| `retries` | high / default-with-idempotence | survive transient broker unavailability |
| `max.in.flight.requests.per.connection` | `≤ 5` | required for idempotence to preserve ordering |

(Applied as `mp.messaging.outgoing.product-updated.<kafka-prop>=...` etc.)

---

## 5. Consumer hardening (DLQ)

For each `@Incoming` channel — `product-updated` and `product-deleted` in
`featured-products-service`, and `price-changed` in products-service — add to
`application.properties`:

- `failure-strategy=dead-letter-queue`
- a `*-dlq` dead-letter topic (e.g. `product-updated-dlq`)
- bounded retry before dead-lettering
- an explicit consumer `group.id` (today it defaults to the application name)

**Idempotency is retained, not replaced.** The existing UUID-upsert logic is what makes
at-least-once delivery safe; the DLQ only changes what happens to a message that fails
*every* attempt (isolated for inspection instead of blocking the partition forever). No
separate processed-event-id dedup table is introduced in this round.

---

## 6. Mongo producers (price-service, orders-service) — design note only

Out of implementation scope here, but the intended end-state is recorded so the pattern stays
uniform.

The same outbox maps to Mongo as an **`outbox` collection** written together with the
business document. The catch: writing two documents atomically (business doc + outbox doc)
requires a **multi-document transaction**, which MongoDB only supports on a **replica set** —
the current deployment is standalone. Two options, to be decided when these services are
tackled:

1. **Enable a single-node replica set** and write business doc + outbox doc in one Mongo
   transaction (closest parallel to the Postgres design).
2. **Embed the outbox in the aggregate document** (an `pendingEvents` array updated in the
   same single-document write), with the relay reading and clearing it — keeps single-document
   atomicity, no replica set needed, but couples event state to the aggregate.

The replica-set prerequisite is the open infra decision blocking option 1. This spec does
**not** resolve it; it flags it for the follow-up task that implements price-service /
orders-service. (price-service review #11 and #12 are the concrete drivers.)

---

## 7. Failure-mode walkthrough (the consistency proof)

| Scenario | Outcome | Why consistent |
|---|---|---|
| Crash **before** commit | No business change, no outbox row | Atomic — both roll back together |
| Crash **after** commit, **before** relay runs | Business change + outbox row persisted; event not yet sent | Relay picks the unpublished row up on next tick → event delivered |
| Broker **down** when relay sends | `send()` fails; `published_at` stays NULL | Row retried on subsequent ticks until the broker accepts it |
| Relay sends, **ack lost** before `published_at` stamped | Row re-sent next tick → duplicate at broker | Consumer UUID-upsert is idempotent; `enable.idempotence` also de-dups at broker |
| Two service instances run the relay concurrently | Each grabs disjoint rows | `FOR UPDATE SKIP LOCKED` prevents double-claim |
| Consumer hits an un-processable (poison) message | Retried up to the bound, then routed to `*-dlq` | Partition keeps flowing; bad message isolated for inspection |

**No row in this table leaves a committed business write with a permanently unpublished
event** — which is the property the current design lacks.

---

## 8. Trade-offs

- **Polling vs Debezium CDC.** Polling adds a small, steady DB read load and ~poll-interval
  latency, but needs **zero new infrastructure** — it fits the single-broker, no-Kafka-Connect
  setup. Debezium would cut latency and remove polling, but adds Kafka Connect + a connector
  to operate. Polling is chosen deliberately for the current infra; Debezium remains a clean
  future swap (the outbox table is the same).
- **Outbox growth.** Published rows accumulate. Add a retention step — either delete on
  publish, or a periodic purge of `published_at < now() - N days` — so the table and its
  partial index stay small.
- **Ordering.** Guaranteed **per aggregate** (key = `aggregate_id`, single partition per
  key); not a global total order across all products. This matches the consumers' per-product
  upsert semantics.
- **Durability ceiling.** With **RF=1** a broker disk loss still loses acked messages — the
  outbox makes the *producer→broker* hop reliable, not the broker itself. Raising RF / broker
  count is a separate infra change (§2 non-goals).

---

## 9. Rollout sketch (follow-up tasks, not part of this spec)

Per the repo's Task Session Policy, each phase is its own session, and a full task plan lives
in `docs/tasks/` as a follow-up:

1. **products-service outbox** — table + write-path + relay + producer hardening (the
   reference implementation).
2. **Consumer DLQ** — `failure-strategy` + `*-dlq` topics across the three `@Incoming`
   channels.
3. **Mongo producers** — resolve the replica-set decision (§6), then apply the outbox to
   price-service and orders-service.

---

## 10. Convention compliance checklist

- ✅ Transactional outbox — satisfies `persistence-conventions.md` § "No I/O inside a
  transaction".
- ✅ `@Transactional` on control layer only; the transaction touches only Postgres; the
  Kafka `send()` is outside it (in the relay).
- ✅ Kafka payloads camelCase; `JsonbSerializer` retained.
- ✅ Reuses existing `products-api` event classes — no new DTOs, no HTTP contract change
  (so the OpenAPI gate does not apply; this spec is the artifact for approval).
- ✅ Liquibase changeset for schema (no manual schema changes).
