# ADR-0011: The order saga is orchestrated by orders-service over the transactional outbox

- **Status:** Accepted
- **Date:** 2026-08-11
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/specs/order-lifecycle.md` (§4), `docs/specs/payment.md`,
  `docs/adr/0002-transactional-outbox-and-mongo-replica-set.md`,
  `docs/conventions/architecture-conventions.md`

## Context

Committing an order has to do three things across three services that own three databases: move the
order to a committed state (orders-service, Mongo), hold the stock (products-service, Postgres), and
take the money (payment-service, Postgres). There is no transaction that spans them, so the sequence
either has a coordination model or it has a set of half-finished orders.

Before this change there was neither. `saveOrder` called products-service and price-service
synchronously over REST and had no compensation of any kind: a failure after the first call left
whatever it had already done in place, and confirmation published a single fire-and-forget event that
only analytics consumed. Nothing held stock, so overselling was not a race — it was guaranteed. The
order lifecycle spec was written to close that gap, and §4 is the part that needs a decision rather
than an implementation.

The platform already publishes business events through the transactional outbox adopted in
**ADR-0002**, and `outbox-common` already carries the relay and the trace propagation every service
reuses.

## Decision drivers

- **The invariant has one owner.** Which transitions are legal is a property of the order aggregate.
  Whatever coordinates the steps has to be able to enforce it.
- **Every failure needs a defined outcome.** Not "logged" — an actual terminal state, with whatever
  was taken given back.
- **A step that never answers is a failure too.** Held stock that nothing releases is worse than a
  rejected order, because it is silent and permanent.
- **No new infrastructure category.** Kafka, the outbox and the relay already exist and are already
  operated.
- **The flow has to be readable.** When an order is stuck, someone has to be able to say where and
  why without reconstructing it from four services' logs.

## Decision

The saga is **orchestrated by orders-service**, and every command and reply travels over each
service's **existing transactional outbox** rather than synchronous REST.

1. **orders-service is the coordinator.** It owns the aggregate and the state machine, so it decides
   which step runs next and which replies are legal. Participants answer commands; they do not
   decide the order's fate.

2. **Two steps, in order: reserve stock, then capture payment.** Stock first, because a rejected
   reservation costs nothing to undo and a capture does.

3. **Commands and replies are outbox messages.** Each participant writes its reply and its own state
   change in one local transaction, which is the whole point of ADR-0002: a reply can be lost in
   transit, but it can never be lost relative to the work it reports.

4. **Every failure compensates to a terminal state.** Reservation rejected → `CANCELLED`, nothing to
   undo. Payment failed after reservation → `release-stock`, then `CANCELLED`. A compensation is
   itself an outbox command, so it is at-least-once and must be a no-op when repeated.

5. **Every step has a deadline, and a sweep enforces it.** A step with no reply by its deadline is
   treated as failed and compensated. This is not a nicety: reservations have no TTL, so the sweep is
   the only thing in the system that ever releases stock a stalled saga is holding.

6. **Idempotency is keyed by `orderId` + step id**, end to end — including the payment provider's own
   idempotency key, so a redelivered capture returns the original charge instead of making a second
   one.

## Considered options

| Area | Chosen | Rejected | Why rejected |
|---|---|---|---|
| Coordination | Orchestration from orders-service | Choreography — each service reacts to the previous one's event | Splits the state machine across services that do not own the aggregate. Every participant would need to know the whole lifecycle to know what to emit next, and "which order is stuck where" stops having a single answer |
| Coordinator location | orders-service | A separate saga/workflow service | A third service that owns no data, for one saga; the invariant it enforces belongs to the order aggregate anyway |
| Transport | Kafka via the existing outbox | Synchronous REST between participants | This is what the old `saveOrder` did. A call that times out leaves the caller unable to distinguish "not done" from "done, answer lost", which is exactly the case that double-charges |
| Transport | Kafka via the existing outbox | A workflow engine (Temporal, Camunda) | Real capability, real operational surface, for two steps. Revisit if the saga grows fulfilment and returns |
| Atomicity | Local transaction + outbox (ADR-0002) | Two-phase commit across Mongo and Postgres | Not available across these stores, and a blocking coordinator is a worse failure mode than compensation |
| Stalled steps | Per-step deadline + sweep | Rely on the relay's retries | The relay abandons a poison record after `maxRetries` and logs an ERROR. That is a silent permanent stock leak |
| Delivery | At-least-once + idempotent handlers | Exactly-once semantics | Same rationale as ADR-0002: costlier, and the dedup keys already absorb duplicates |

## Consequences

**Positive**
- The lifecycle is enforced in one place, and an illegal reply is rejected by the same table that
  rejects an illegal API call.
- Every failure path ends somewhere defined, with reservations released.
- No participant blocks on another; a slow payment provider does not hold a stock lock or an HTTP
  thread.
- Reuses infrastructure that is already built, operated and traced — the outbox relay re-parents its
  producer span on the originating request, so one order is still one trace.
- The stalled-saga case is observable rather than theoretical: it has a metric and an alert.

**Negative / costs**
- **Committing is asynchronous.** `POST /orders/{id}/confirm` returns when the order is committed and
  payment has been *requested*, not when it is paid. The buyer is thanked at that point and sees the
  outcome in their order history, which reads "Processing" until the saga lands. Nothing pushes or
  polls the result onto the confirmation page — a buyer who watches it learns nothing.
- **orders-service knows every step.** That is the trade against choreography, taken deliberately —
  adding a step means changing the coordinator.
- **One case remains genuinely unrecoverable**, and it is accepted rather than solved: the provider
  takes the money and the answer never arrives at all. Nothing is recorded, so nothing can be
  replayed; the step deadline cancels the order with money taken. It is alerted on, and
  §9 of `docs/specs/payment.md` still records that no one owns resolving it.
- **Deadlines are a tuning surface.** Set too short they cancel orders that would have succeeded; too
  long they hold stock through an outage.
