# Spec: Order Lifecycle (state machine, saga, payment & inventory)

**Status:** Draft — design spec for review (Spec gate, step 1 of the workflow).
**Scope of this spec:** analysis + target design. **No code changes.** The OpenAPI contract
(step 2) is delivered alongside; implementation is gated on both being approved.
**Owning service:** `orders-service` (Mongo), which owns the order aggregate.

---

## 1. Problem statement

The order flow works end to end — a buyer fills a cart, checks out, confirms, and the sale reaches
the analytics warehouse — but it is a **two-state flow with no money and no stock**. There is no
specification describing what an order *is*, which transitions are legal, or what happens when a
step fails. As a result the implementation has drifted into several defects that a written
lifecycle would have prevented.

### What exists today

```
POST /api/cart/items       → Cart document (product id + quantity only, no price)
POST /api/cart/checkout    → build Order → saveOrder → delete cart
     saveOrder             → REST to products-service (title, price, promotions)
                           → REST to price-service (applies discounts, returns total)
                           → status INITIATED, persist
POST /api/orders/{id}/confirm
                           → status CONFIRMED + outbox entry in ONE Mongo transaction
                           → OutboxRelay publishes `order-initiated`
                           → consumed by analytics-service only
```

`OrderStatus` is `INITIATED, CONFIRMED`. Nothing else exists.

### Defects this lifecycle is meant to close

| # | Defect | Location |
|---|---|---|
| 1 | Checkout writes two collections with no transaction: a crash between `saveOrder` and the cart delete leaves an order **and** a live cart, so the buyer re-checkouts and gets a duplicate order | `CartService.java:131` |
| 2 | `confirmOrder` has no status guard — two calls insert **two outbox entries** and publish the event twice | `OrderService.java:141` |
| 3 | Confirm uses `ReplaceOptions().upsert(true)`, which resurrects an order deleted concurrently | `OrderService.java:157` |
| 4 | No optimistic locking on the aggregate — concurrent confirm/update is a lost update | `entity/Order.java` |
| 5 | `updateOrder` and `deleteOrder` have no status guard, and `updateOrderFromRequest` maps `products` **without re-pricing** — quantities can change after confirmation while the price stands, and nothing emits a cancellation, so the warehouse diverges permanently | `OrdersResource.java:45,60` |
| 6 | Promotion percentages are summed unclamped: two 60% promotions produce a **negative price** | `OrderService.java:77` |
| 7 | Promotion windows use strict `isBefore` at both ends, so a promotion starting or ending today never applies | `OrderService.java:121` |
| 8 | Price is captured at create and never revalidated; there is no expiry on `INITIATED` orders, and catalog prices genuinely move (`price-changed`) | `OrderService.java:98` |
| 9 | Money is `Double`, rounded via `String.format("%.2f")` in two separate layers, with no currency field anywhere | `Order.java`, `PricingService.java:78` |
| 10 | `validationNumber` is persisted to Mongo and published on Kafka; all three card fields are in the **published** OpenAPI contract | `OutboxEventFactory.java:56` |

**Verified *not* a defect:** price-service discounts each unit price, but `saveOrder` reads back only
the *total*, so the stored order keeps list price plus `percentageOff`. Analytics'
`qty × price × (1 − pct/100)` (`IngestionService.java:75`) therefore reproduces the total exactly.
There is no double-discount.

---

## 2. Goals / non-goals

**Goals**
- A single documented state machine for an order, with an explicit table of legal transitions.
- A failure model: what happens when stock cannot be reserved, payment fails, or a reply is lost.
- Compensating actions for every step that can fail after a prior step succeeded.
- Idempotency rules strong enough that at-least-once delivery cannot double-charge or double-ship.
- Remove card data from the order aggregate and its contract.

**Non-goals**
- **This spec implements nothing.** No Java, no tests, no schema migration.
- Choosing a payment provider. Payment is specified as an integration shape (opaque token), not a
  vendor.
- Shipping rates, tax calculation, and address validation. `SHIPPED`/`DELIVERED` are modelled as
  states so the machine is complete, but their inputs are out of scope here.
- Multi-currency conversion. A currency *field* is required; conversion is not.

---

## 3. The state machine

```
                    ┌──────────────────────────────── cancel ───────────────────────────┐
                    │                                                                   │
                    ▼                                                                   │
INITIATED ──confirm──> CONFIRMED ──reserved──> RESERVED ──captured──> PAID ──> SHIPPED ──> DELIVERED
    │                      │                       │                   │
    │                      │ reservation failed    │ payment failed    │ refund
    ▼                      ▼                       ▼                   ▼
CANCELLED             CANCELLED               CANCELLED            REFUNDED
                                          (release stock first)  (restock first)
```

| State | Meaning | Mutable? |
|---|---|---|
| `INITIATED` | Checked out from the cart and priced. Buyer has not committed. | **Yes — the only mutable state** |
| `CONFIRMED` | Buyer committed. Saga started. | No |
| `RESERVED` | Stock reserved against the order. | No |
| `PAID` | Payment captured. | No |
| `SHIPPED` | Handed to fulfilment. | No |
| `DELIVERED` | Terminal, success. | No |
| `CANCELLED` | Terminal, failure or buyer cancellation. | No |
| `REFUNDED` | Terminal, money returned after payment. | No |

`CONFIRMED` deliberately keeps the meaning it has today, so the existing `order-initiated` event and
the analytics contract are unchanged by this design. The new states extend the flow rather than
renaming it.

> **Since implemented:** revenue moved to `order-paid` as §6 required, which left `order-initiated`
> with no consumer at all. It was subsequently deleted outright — producer, channel and topic. What
> opens the saga on confirm is the `reserve-stock` command; nothing announces a confirmation any
> more. See §5.

### 3.1 Legal transitions

| From | To | Trigger |
|---|---|---|
| `INITIATED` | `CONFIRMED` | buyer confirms **and catalog prices still match the quote** (§10.1) |
| `INITIATED` | `CANCELLED` | buyer cancels, or order expires |
| `CONFIRMED` | `RESERVED` | stock reserved |
| `CONFIRMED` | `CANCELLED` | reservation failed, or buyer cancels |
| `RESERVED` | `PAID` | payment captured |
| `RESERVED` | `CANCELLED` | payment failed or buyer cancels — **compensate: release stock** |
| `PAID` | `SHIPPED` | fulfilment dispatched |
| `PAID` | `REFUNDED` | refund — **compensate: restock** |
| `SHIPPED` | `DELIVERED` | delivery confirmed |
| `SHIPPED` | `REFUNDED` | return accepted — **compensate: restock** |

**Every transition not in this table is rejected with `409`.** In particular: confirming an already
confirmed order, and updating or deleting anything past `INITIATED`.

> **Since written:** the two fulfilment rows had no trigger — `SHIPPED` and `DELIVERED` were legal
> but unreachable, because nothing in the system could move an order past `PAID`. What produces them
> is specified in `docs/specs/order-fulfilment.md`. `REFUNDED` remains unreachable, as
> `docs/specs/payment.md` §2 records.

### 3.2 Immutability

Only `INITIATED` orders may be updated or deleted. This closes defect 5. `PUT /orders` and
`DELETE /orders/{orderID}` return `409` in every other state. A buyer who wants out of a committed
order uses `POST /orders/{orderID}/cancel`, which is a *transition*, not a mutation, and emits an
event so downstream read models converge.

---

## 4. Saga

### 4.1 Orchestration, not choreography

The saga is **orchestrated by orders-service**. It owns the order aggregate, so it owns the state
machine; putting the coordinator anywhere else would split the invariant across two services.
Commands and replies travel over Kafka using each service's existing transactional outbox
(ADR-0002) — **not** synchronous REST, which is how `saveOrder` works today and is precisely why
that path has no compensation story.

```
orders-service                    products-service              payment
     │  reserve-stock (cmd)              │                         │
     ├──────────────────────────────────>│                         │
     │  stock-reserved / -rejected       │                         │
     │<──────────────────────────────────┤                         │
     │  capture-payment (cmd)            │                         │
     ├────────────────────────────────────────────────────────────>│
     │  payment-captured / -failed       │                         │
     │<────────────────────────────────────────────────────────────┤
     │  release-stock (compensation, only on payment failure)      │
     ├──────────────────────────────────>│                         │
```

### 4.2 Compensations

| Failure | Compensation | Terminal state |
|---|---|---|
| Reservation rejected | none needed — nothing was taken | `CANCELLED` |
| Payment failed after reservation | `release-stock` | `CANCELLED` |
| Buyer cancels while `RESERVED` | `release-stock` | `CANCELLED` |
| Refund after `PAID` / `SHIPPED` | `restock` + provider refund | `REFUNDED` |

A compensation is itself a command over the outbox, so it inherits the same at-least-once delivery
and must be idempotent: releasing an already-released reservation is a no-op, not an error.

### 4.3 Idempotency

Delivery is at-least-once in both directions. Therefore:

- Every command and reply carries `orderId` **and** a step identifier; consumers deduplicate on that
  pair and treat a repeat as a no-op returning the original outcome.
- Stock reservation is keyed by `orderId`, so re-delivering `reserve-stock` reserves once.
- Payment capture must use the provider's idempotency key, set to the order's saga step id, so a
  redelivered capture cannot double-charge.
- State transitions are guarded by the table in §3.1: a reply that would drive an illegal transition
  is discarded as a duplicate, not applied.

### 4.4 Stranded sagas

`AbstractOutboxRelay` retries a failing record up to `maxRetries`, then **marks it failed and skips
it permanently**, logging an ERROR. A saga whose command is abandoned that way stalls forever in an
intermediate state with stock reserved and no payment attempted.

This spec requires:
- A **timeout per saga step**. A step that receives no reply within its deadline is treated as
  failed and compensated.
- Failed outbox records must be **observable** — a metric and an alert, not just a log line.
- A dead-saga sweep that moves timed-out orders to `CANCELLED` with compensations applied.

**These are mandatory, not best-effort.** Per §10.3 reservations have no TTL, so nothing else ever
releases stock. Without the step deadline and the sweep, a single stalled saga takes inventory out
of sale permanently and silently.

---

## 5. Events

Existing at the time of writing:

| Event | Emitted when | Consumed by |
|---|---|---|
| `order-initiated` | order confirmed | analytics-service |

> **Since implemented:** `order-initiated` no longer exists. Once revenue moved to `order-paid` it
> had no consumer, and a produced event nobody reads costs the publish, looks like a working
> integration, and delivers nothing — so it was removed rather than left as an orphan. Confirming an
> order now writes exactly one outbox entry: the `reserve-stock` command that opens the saga.

New (contracts specified in a later task, names fixed here):

| Event | Emitted when |
|---|---|
| `order-cancelled` | order reaches `CANCELLED` |
| `order-paid` | payment captured (`PAID`) |
| `reserve-stock` / `stock-reserved` / `stock-rejected` | reservation command and replies |
| `release-stock` | compensation |
| `capture-payment` / `payment-captured` / `payment-failed` | payment command and replies |

---

## 6. Consequence for analytics

**This changes what the dashboard means, and the change must be deliberate.**

`analytics-service` currently treats `order-initiated` — i.e. *confirmation* — as revenue. With no
payment in the system that is defensible: confirmation is the furthest the order ever gets. Once
`PAID` exists it stops being defensible, because a confirmed-but-unpaid order would be counted as
money taken.

Required when payment lands:
- Revenue follows `order-paid`, not `order-initiated`.
- `IngestionService` consumes the new event; `order-initiated` either stops feeding the fact table
  or feeds a separate "orders placed" measure.

> **Since implemented:** revenue follows `order-paid`. The second point was settled the other way
> than "a separate measure": no consumer wanted an orders-placed figure, so the event was deleted.
> If that measure is wanted later it should be designed alongside whatever consumes it, rather than
> inherited as an orphan producer.
- `order-cancelled` and refunds must reverse or negate previously counted rows, otherwise the
  warehouse only ever grows.

Until then, the dashboard's "revenue" is **orders placed**, and the spec for the analytics dashboard
(`docs/specs/analytics-dashboard.md`) should be read with that caveat.

---

## 7. Money

`Double` is replaced throughout the order and pricing path:

- **Amounts** are `BigDecimal` at scale 2, rounded `HALF_UP`. The contract lives in
  `orders-api`'s `Money`. Binary floating point cannot represent most decimal fractions, so a price
  held as a `double` is already not the number that was entered, and the error compounds through
  every total derived from it.
- **Rounding happens once per amount, where that amount is finalised.** A discounted unit price is
  rounded as it is set, because it is itself published on the order line; the order total is then
  the exact sum of those and needs no second rounding. Previously `String.format("%.2f")` ran in
  both `ApplyPromotionsService` and `PricingService`, and the first result was discarded — the
  surviving total was rounded from unit prices that had never been rounded at all.
- **Currency** is an explicit field on the order and on every amount crossing a service boundary,
  defaulting to `EUR`. There is one currency and no conversion; the field exists so that amounts
  are not silently currency-less.
- **Percentages stay `Double`.** A discount rate is not an amount. It is only ever applied to a
  `BigDecimal` price, and the result of that application is what gets rounded.

Storage follows the type: `product.price` and `fact_sales_line.unit_price` / `line_revenue` are
`numeric(12,2)` in Postgres, and Mongo stores `Decimal128`. Leaving the warehouse on
`DOUBLE PRECISION` would have let it drift from the operational store, so its migration belongs to
the same change.
- **Discounts** are clamped to the range 0–100 after summing (defect 6), and promotion windows use
  inclusive comparisons at both ends (defect 7).

---

## 8. Card data

Card details **never enter the order aggregate**. `card_number`, `expiration_date` and
`validation_number` are removed from `OrderDTO`, from the `Order` entity, from the outbox payload,
and from the published OpenAPI.

Payment integrates by holding an **opaque token reference** issued by the payment provider. The
order stores that reference and nothing else; the platform never sees or persists a PAN, expiry, or
CVV, and no card data is ever written to Mongo or published to Kafka.

Removing these fields from the contract is safe: they are referenced only within `backend/`, with no
`frontend/` or `e2e/` usage, and nothing populates them today.

---

## 9. Failure-mode walkthrough

| Scenario | Today | Under this spec |
|---|---|---|
| Crash between `saveOrder` and cart delete | Order exists, cart survives → buyer re-checkouts → **duplicate order** | Checkout writes the order and clears the cart in one Mongo transaction (both collections, same session — the mechanism `confirmOrder` already uses) |
| `confirm` called twice | Status rewritten, **two outbox entries**, event published twice | Second call sees `CONFIRMED`, is rejected `409`; no second entry |
| `confirm` races `delete` | `upsert(true)` **resurrects** the deleted order | `delete` is illegal past `INITIATED`; confirm is a guarded transition on an existing document |
| Payment captured, reply lost | n/a — no payment | Step times out → compensation issued → provider idempotency key makes the retry safe; a late reply drives an illegal transition and is discarded |
| Poison outbox record | Abandoned silently after `maxRetries`, ERROR log only | Step deadline fires, saga compensates to `CANCELLED`, failed record raises a metric and an alert |
| Buyer changes quantities after confirming | Allowed; **price not recalculated** | `409` — only `INITIATED` is mutable |

---

## 10. Decisions

These were open during drafting and are now settled.

### 10.1 Price is revalidated at confirm

Confirming re-reads current catalog prices and **rejects with `409` if anything moved**, rather than
honouring the quote from checkout. This closes defect 8: no order is ever billed at a stale price.

Two consequences that constrain the implementation:

- **The revalidation must happen before the transaction opens.**
  `docs/conventions/persistence-conventions.md` forbids network I/O inside a transaction, and
  `confirmOrder` currently wraps the status write and the outbox insert in one Mongo transaction.
  The products-service call therefore runs first, and only its *result* enters the transaction.
- **The `409` must say what changed**, so a client can show the buyer the difference rather than a
  bare failure. The error body carries the affected products and their old and new prices.

### 10.2 No unconfirmed-order expiry

Because price is revalidated at confirm, a stale `INITIATED` order is harmless — an old quote can
never be billed. No expiry sweeper is needed, and orders may sit in `INITIATED` indefinitely.

> **Reversed: uncommitted orders now expire after 24h.** The reasoning above still holds and is not
> what changed — an expired order could not have been billed wrongly, because §10.1 makes that
> impossible. What it missed is that nothing can *act* on an abandoned order either: the only path
> that confirms one is the checkout flow that created it, so once that page is gone the order is
> unreachable rather than merely stale. Left alone they accumulate in the collection and in the
> buyer's own history, as orders they never placed and cannot remove.
>
> `InitiatedOrderExpirySweep` cancels them with `status_reason` `INITIATED_EXPIRED`, on
> `orders.initiated.ttl` (default `PT24H`, configurable — the right window is a business call).
> It compensates nothing, because an `INITIATED` order holds no stock and has taken no money; that
> is the entire difference between it and the saga sweep of §4.4, and the reason it must refuse to
> touch anything that has left `INITIATED`. Counted separately as `orders.expired`: a rise there
> says something about checkout, not about demand.

### 10.3 Reservations have no TTL

Stock stays reserved until something explicitly releases it. There is no independent reaper.

**This makes §4.4 load-bearing rather than advisory.** With no TTL, the saga's own step deadline is
the *only* mechanism that ever releases stock. If the step timeout, the dead-saga sweep, and the
alert on abandoned outbox records are not built, a stalled saga removes inventory from sale
**permanently**, with nothing to notice or recover it. These are therefore requirements of the first
implementation phase that introduces reservations, not follow-ups.

### 10.4 Fulfilment is all-or-nothing

An order ships once, completely. State stays on the order aggregate; no per-line state, no per-line
events, and the warehouse keeps its one-row-per-line model.

### 10.5 Refunds are full-order only

`REFUNDED` negates the whole order. Analytics reverses by order id, which its existing
delete-by-order-id ingestion already does cleanly (`IngestionService`). `REFUNDED` stays terminal and
accurate. Partial refunds would require per-line reversal and would make the status ambiguous.

### 10.6 Checkout prices before it opens the transaction

Checkout writes the order and removes the cart in one transaction, closing defect 1. Pricing stays
outside it, for the same reason as §10.1: it is REST traffic to two services, and holding write locks
across a network round trip is how a slow dependency becomes a stalled database. Checkout therefore
runs in three steps - price, then the paired writes, then the metrics.

The `orders_created` counter is incremented **after** the commit rather than inside the transaction
body. An abort rolls back the writes but cannot roll back a counter, so recording inside would
overcount every checkout that aborts and is retried.

---

## 11. Convention compliance checklist

Checked off after implementation, against what enforces each — not against a reading of the code.
Most of these are held by a build gate rather than by review, which is the point: a checklist nothing
enforces is back to being unchecked the next time someone edits the file.

- [x] BCE layering: state machine and saga coordination live in `control/`; DTOs in `boundary/dto/`;
      event payloads in `control/events/` (`docs/conventions/architecture-conventions.md`)
      — enforced by `BceArchitectureTest`
- [x] JSON: snake_case, nulls omitted, ISO-8601 dates — including on all new events
      (`docs/conventions/json-serialization-conventions.md`)
      — enforced by the shared saga wire-contract fixtures, which both ends parse
- [x] Exceptions: illegal transitions are `FunctionalException` with an `errorCode`, mapped by the
      existing `GlobalExceptionHandler`; no try-catch in resources
      (`docs/conventions/exception-handling-conventions.md`)
      — `IllegalOrderTransitionException` → `409` `ILLEGAL_ORDER_TRANSITION`, asserted end to end
- [x] Persistence: `@Transactional` on control-layer mutating methods only; no network I/O inside a
      transaction — the outbox carries every cross-service call
      (`docs/conventions/persistence-conventions.md`)
      — enforced by `TransactionalRulesArchTest`
- [x] Testing: unit tests for every transition in §3.1 including the illegal ones; Testcontainers
      integration tests for the crash windows in §9 (`docs/conventions/testing-conventions.md`)
      — §3.1 is covered exhaustively and parameterized off the table itself, so a state added to the
      enum without a table entry is tested as illegal. Of §9, the checkout crash window and the two
      `409` cases run against real Mongo; the lost reply, the poison record and the deadline are
      unit tests, because what they assert is which writes are issued and in what transaction
- [x] Logging: state transitions logged with `orderId` and both states; correlation via MDC
      (`docs/conventions/logging-conventions.md`)
      — every transition names `from` and `to`, including the ones that are refused or discarded
- [x] An ADR records the orchestration-over-choreography decision (§4.1) once approved
      — `docs/adr/0011-orchestrated-saga-over-the-outbox.md`
