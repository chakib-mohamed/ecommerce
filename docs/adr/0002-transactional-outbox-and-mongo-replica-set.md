# ADR-0002: Transactional outbox for event publishing (+ MongoDB single-node replica set)

- **Status:** Accepted
- **Date:** 2026-06-06
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/specs/kafka-transactional-consistency.md`, `docs/conventions/persistence-conventions.md`

## Context

Every Kafka **producer** in the platform wrote to its database and published to Kafka as two
separate, uncoordinated operations — the classic **dual-write problem**. There was no atomic
boundary spanning "business state changed" and "event published", so any failure between the two
left the stores permanently inconsistent: the DB advanced but the event was silently lost, and
downstream read models never converged.

The three producers were inconsistent and all unsafe to some degree:

- **products-service** (Postgres/JPA) — published from a CDI `@Observes(AFTER_SUCCESS)` hook. Best
  of the three (no phantom events), but a crash between commit and `send()`, or a broker outage at
  send time, still lost the event with nothing recording that it was owed.
- **price-service** (Mongo) — wrote to Mongo, then called `emitter.send()` with the returned
  `CompletionStage` **discarded**; crash or broker rejection = silent loss.
- **orders-service** (Mongo) — pure fire-and-forget `emitter.send()` straight from the resource,
  not even gated on commit.

The project's own `persistence-conventions.md` already mandated the fix ("for services that write
to both a DB and Kafka, use the transactional outbox pattern") but no service implemented it. On
the consumer side, `featured-products-service` and the products-service `price-changed` consumer
were idempotent (UUID upserts) but ran auto-ack with **no `failure-strategy` and no dead-letter
queue**, so a poison message would block its partition forever.

## Decision drivers

- **No lost events:** once a business write commits, its event must be published **at least once**.
- **Bounded eventual consistency** (lag = poll interval + relay time, not "forever" on a crash).
- **Stay within current infrastructure** — no Kafka Connect, no Debezium, no new broker count.
- **One uniform pattern** across the Postgres producer and the two Mongo producers.
- **Poison-message isolation** on consumers instead of infinite redelivery.

## Decision

Adopt the **transactional outbox** pattern for all event producers, with **at-least-once** delivery
absorbed by existing consumer idempotency. Concretely:

1. **Persist the event in the same transaction as the business write.** An `outbox` row/document is
   committed atomically with the business state (Postgres: a row in the same JTA transaction;
   Mongo: a doc in the same `ClientSession.withTransaction`). The transaction touches **only the
   database** — no network I/O inside it, honoring `persistence-conventions.md`.

2. **A separate scheduled relay publishes to Kafka.** A `@Scheduled` relay selects unpublished rows
   ordered by `created_at`, emits each to its topic with the Kafka **message key = `aggregate_id`**
   (per-aggregate ordering), and stamps `published_at` on broker ack. Un-acked rows are retried on
   the next tick. Postgres uses `SELECT ... FOR UPDATE SKIP LOCKED` so multiple instances never
   double-publish a row.

3. **Producer Kafka hardening:** outgoing channels set `acks=all`, `enable.idempotence=true`,
   high `retries`, and `max.in.flight.requests.per.connection ≤ 5` — so the relay's `send()` is
   durable and broker-dedup-safe.

4. **Consumer dead-letter queues:** each `@Incoming` channel gets `failure-strategy=dead-letter-queue`,
   a `*-dlq` topic, bounded retry, and an explicit `group.id`. Existing UUID-upsert idempotency is
   **retained, not replaced** — it is what makes at-least-once safe; the DLQ only isolates messages
   that fail *every* attempt.

5. **MongoDB runs as a single-node replica set** (`--replSet rs0` + idempotent `rs.initiate`,
   `?replicaSet=rs0` on connection strings). This is the **prerequisite** that lets the Mongo
   producers write business doc + outbox doc atomically in one multi-document transaction — the same
   shape as the Postgres reference, with event state decoupled from the aggregate. Testcontainers'
   `MongoDBContainer` already boots as a single-node replica set, so the test suite is unaffected;
   only dev/prod compose changes.

## Considered options

| Area | Chosen | Rejected | Why rejected |
|---|---|---|---|
| Consistency mechanism | Transactional outbox (poll relay) | Keep `AFTER_SUCCESS` / fire-and-forget | Cannot guarantee a committed write ever produces its event |
| Change capture | Scheduled polling relay | Debezium CDC | Adds Kafka Connect + a connector to operate; polling needs zero new infra (outbox table is the same, so Debezium stays a clean future swap) |
| Delivery guarantee | At-least-once + consumer idempotency | Exactly-once | EOS is far costlier; UUID-upsert consumers already absorb duplicates |
| Mongo atomic write | Single-node replica set + multi-doc txn | Embed `pendingEvents` array in the aggregate (no replica set) | Couples outbox to each aggregate's schema; complicates the relay (per-aggregate array scans vs one `{published_at: null}` query) |
| Poison messages | DLQ + bounded retry | Auto-ack as-is | A bad message redelivers forever and blocks the partition |

## Consequences

**Positive**
- No committed business write can be left with a permanently unpublished event (see the failure-mode
  walkthrough in the spec, §7); consistency lag is bounded by the poll interval, not unbounded on crash.
- One uniform outbox shape across Postgres and Mongo producers; relay/purge logic is the same in form.
- Poison messages are isolated to a `*-dlq` instead of blocking partitions.
- No new infrastructure components — fits the single-broker dev setup.

**Negative / costs**
- The relay adds a small, steady DB read load and ~poll-interval publish latency.
- **Outbox growth:** published rows accumulate, requiring a retention/purge step.
- **Durability ceiling:** with broker **RF=1**, a broker disk loss still loses acked messages — the
  outbox makes the *producer→broker* hop reliable, not the broker itself. Raising RF / broker count
  is a separate infra decision.
- The replica-set requirement adds a one-time `rs.initiate` to Mongo startup (idempotent) and
  `?replicaSet=rs0` to connection strings.
