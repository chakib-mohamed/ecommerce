# Refunds

Returning a buyer's money for an order that was paid for, and putting the platform back into a
consistent state afterwards: stock back on sale, revenue no longer counted, order terminal.

Depends on `docs/specs/order-lifecycle.md` (states and compensations) and `docs/specs/payment.md`
(the provider integration). This spec covers only what those two leave unbuilt.

---

## 1. Problem

`REFUNDED` is in the state machine, in the published contract, and in the payment record's status
enum. Nothing can reach it. The gap is in three places, and each one alone is enough to stop a
refund happening:

| Where | What is missing |
|---|---|
| Trigger | Nothing anywhere asks for a refund. orders-service has no refund endpoint, no refund method, and no way to publish `refund-payment` — the topic has a consumer and no producer. |
| Reply | `PaymentService.refund` calls the provider, marks the record `REFUNDED`, and **writes no outbox entry**. The orchestrator would never learn the outcome, so the order could not leave `PAID` even if the command arrived. |
| Reversal | analytics-service consumes `order-paid` only. A refunded order stays counted as revenue for ever. |

So the current implementation is not merely unreachable — wiring a producer to it would produce a
charge reversed at the provider, an order stuck in `PAID`, and a dashboard still counting the sale.

There is a fourth, smaller fault. `refund` is `@Transactional` and calls the provider inside that
transaction, which `backend/CLAUDE.md` forbids: it ties a database connection to a remote timeout
for no benefit, since a rollback cannot un-refund a card. `capture` was fixed for exactly this and
is guarded by `TransactionBoundaryTest`; `refund` sits beside it doing the opposite.

## 2. Goals and non-goals

**Goals**

- An operator can refund a paid order, in full, and the platform ends consistent.
- The money movement is idempotent: a redelivered command refunds once.
- The outcome is reported, so the order reaches `REFUNDED` rather than waiting.
- Stock returns to sale and revenue stops being counted.

**Non-goals**

- **Partial refunds.** Settled in order-lifecycle §10.5: `REFUNDED` negates the whole order.
  Per-line reversal would make the status ambiguous and the warehouse reversal per-line too.
- **A returns workflow.** Whether goods physically come back, and who inspects them, is a business
  process this platform does not model. A refund here is a money and bookkeeping operation.
- **Buyer-initiated refunds.** See §3; the buyer has no self-service path in this version.
- **Automatic refunds for timed-out captures.** That is payment.md's open question 2 and stays open;
  this spec gives an operator the tool such a policy would need, not the policy.

## 3. Who asks for a refund

**An operator, through the back office.** A new admin-only endpoint on orders-service:

```
POST /api/orders/{orderID}/refund
```

Rejected alternatives, and why:

- **Buyer-initiated.** Needs a returns process, an approval step, and a rule about who may ask for
  what — none of which exist. A buyer-facing button that moves money with no approval is not a
  feature, it is a hole.
- **Automatic, on the timed-out-capture case.** The failure it addresses is real (payment.md §8:
  money taken against a cancelled order) but it is triggered by *uncertainty* — the provider did
  not answer, so the platform does not know whether it holds the buyer's money. Refunding on a
  guess is how a service refunds charges it never made. That case wants reconciliation against the
  provider's records first, and then, if warranted, this endpoint.

The endpoint is a request to refund, not a report that one happened. It returns `202`-style
semantics in the same sense confirm does: the order is committed to refunding and the outcome
arrives later, as the order's status.

## 4. The flow

Orchestrated by orders-service, which owns the aggregate and therefore the state machine. Commands
and replies travel on the transactional outbox, like every other saga step.

```
operator ──POST /orders/{id}/refund──> orders-service
                                          │  guard: PAID or SHIPPED
                                          │  open a refund step (new stepId, deadline)
                                          ▼
                                     refund-payment ──> payment-service
                                                           │ provider refund, by providerRef
                                                           ▼
                          orders-service <── payment-refunded / payment-refund-failed
                                          │
                     ┌────────────────────┴─────────────────┐
              refunded                                  failed
                     │                                      │
        release-stock (restock)                    order stays PAID/SHIPPED,
        order -> REFUNDED                          status_reason records why
        order-refunded ──> analytics
```

**Refund first, then restock.** Putting stock back before the money is returned would advertise
inventory on the strength of a refund that may be refused. The order only becomes `REFUNDED` once
the provider has confirmed.

**A failed refund does not move the order.** It stays `PAID`, and `status_reason` carries the
provider's reason. A refund that cannot be made is an operator problem, and leaving the order in the
state that is actually true is what makes it visible.

## 5. Events

| Topic | Direction | Payload | Status |
|---|---|---|---|
| `refund-payment` | orders → payment | `orderId`, `stepId` | exists, unproduced |
| `payment-refunded` | payment → orders | `orderId`, `stepId`, `providerRef` | **new** |
| `payment-refund-failed` | payment → orders | `orderId`, `stepId`, `reason` | **new** |
| `release-stock` | orders → products | `orderId`, `stepId` | exists, reused — see §6 |
| `order-refunded` | orders → analytics | `orderId` | **new** |

`payment-refunded` is a distinct topic rather than a reuse of `payment-captured`, because the
orchestrator has to tell "the money moved to the buyer" from "the money moved from them". The
existing `reply` helper in payment-service branches on `CAPTURED` versus everything else, so a
refunded record would currently be announced as a *failed capture*, which is why this is a new
path and not a new branch in that one.

## 6. Restock reuses `release-stock`, and that works by accident

A reservation is `HELD` from the moment stock is reserved and is never marked consumed —
`ReservationStatus` has `HELD`, `RELEASED` and `REJECTED`, and nothing sets anything else once an
order is paid for. `StockReservationService.release` increments the product's stock for every
`HELD` row and marks it `RELEASED`.

So `release-stock` on a paid order does exactly what restocking means, and no new command is needed.

**This is worth stating because it is not by design.** The reservation table cannot distinguish
"held for an order in flight" from "sold", so nothing else can either. Two consequences to keep in
view:

- Restock is correct today only because a sale never changes the reservation's state. If a
  `CONSUMED` state is ever introduced — and the model would be clearer for it — `release` stops
  restocking sold goods and this spec's compensation breaks silently.
- The saga's timeout sweep is unaffected: it only considers orders that carry a step deadline, and a
  paid order has none.

## 7. Analytics

`order-refunded` is consumed by analytics-service, which deletes the fact rows for that order. Its
ingestion already deletes by order id, so the reversal is the operation it has, not a new one.

This closes the gap `docs/specs/analytics-revenue.md` records as deliberately unbuilt: the warehouse
only grows, because until refunds exist nothing can take a counted sale back. `CANCELLED` remains
irrelevant to it — it is unreachable from `PAID`, so a cancellation never retracts revenue.

The reversal is a deletion rather than a negative row. The warehouse reports money taken and kept;
a refunded order is not a sale that happened and was undone in the ledger sense, it is a sale that
no longer counts. If refund *reporting* is ever wanted — how much was returned, and when — that
needs its own fact, not a sign flip on this one.

## 8. Idempotency

Delivery is at-least-once in both directions, so:

- The refund step carries `orderId` and a fresh `stepId`, as every step does.
- The provider's idempotency key is the `stepId`, so a redelivered `refund-payment` returns the
  original refund rather than making a second one. This is the same mechanism capture uses, and the
  same reason: it holds even if payment-service loses its database.
- payment-service deduplicates locally too: a record already `REFUNDED` for that order replays its
  reply instead of calling the provider again.
- `release-stock` is already idempotent — releasing a reservation with no `HELD` rows is a no-op.
- The endpoint itself is guarded by the state machine: a second refund request for an order already
  `REFUNDED` is an illegal transition and returns `409`, not a second refund.

## 9. Failure-mode walkthrough

| What happens | Result |
|---|---|
| Provider refuses the refund | `payment-refund-failed`; order stays `PAID` with `status_reason`; no restock. An operator sees a paid order that could not be refunded. |
| Provider does not answer (timeout) | Nothing is written, the command redelivers, the idempotency key makes the retry the same refund. Counted as a gateway fault, which is already alerted on. |
| Reply lost after a successful refund | Command redelivers; the local record is already `REFUNDED`, so the reply is replayed without touching the provider. |
| Refund succeeds, restock message lost | Order is `REFUNDED` and money returned, stock still held. The outbox relay retries; if it exhausts its cap the row is abandoned and stock stays out of sale — visible as inventory that never comes back, and the existing relay alerting is what surfaces it. |
| Refund requested for an order with no charge | payment-service finds no captured payment and replies refunded-with-nothing-to-return, so the saga completes rather than stalls. Today this path returns silently, which is one of the reasons the order could never move. |
| Two operators refund at once | The state machine and the order's version guard admit one; the second gets `409`. |

## 10. Constraints inherited from elsewhere

- **No network I/O inside a transaction** (`backend/CLAUDE.md`). The provider call happens outside
  any transaction, exactly as capture does; the database work on either side of it is wrapped
  explicitly. `TransactionBoundary` and its test already express this for capture and extend to
  refund unchanged.
- **No card data, ever.** A refund needs only the provider's own reference, which the payment record
  already stores. Nothing card-shaped enters this flow at any point.
- **Admin-only.** The endpoint moves money outward; it carries the same role guard as the rest of
  the back office.

## 11. Open questions

1. **Does a refund need a reason, and is it the buyer's business?** An operator refunding will
   usually know why. Storing it costs a field; showing it to the buyer is a support decision.
2. **Should `SHIPPED` be refundable without a return being recorded?** The lifecycle table allows
   `SHIPPED → REFUNDED` and calls it "return accepted", but nothing records that goods came back. As
   specified, an operator can refund a shipped order and restock inventory that is still in a van.
3. **What happens to a refunded order's reservation history?** §6 leaves the rows `RELEASED`, which
   is indistinguishable from a cancellation. Fine for stock arithmetic, poor for auditing.

## 12. Convention compliance

| Rule | How this complies |
|---|---|
| Events over the transactional outbox | Every command and reply is an outbox row committed with its business change |
| snake_case JSON, nulls omitted, ISO-8601 dates | New payloads follow the platform contract |
| No network I/O in a transaction | §10 |
| Idempotent consumers | §8 |
| Errors as `FunctionalException` | Illegal transition → `409`; unknown order → `404` |
| Contract before code | The endpoint is an OpenAPI change, and gate 2 comes before any of this is built |
