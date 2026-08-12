# Analytics follows payment

**Status:** Draft - design spec for review (Spec gate, step 1 of the workflow).

Phase 9 of `docs/tasks/order-lifecycle-plan.md`. Depends on phase 8, which built the payment step and
made `PAID` reachable. This says what the dashboard's numbers mean now that it is.

> **Since implemented:** everything below describes the move off `order-initiated`, and it landed.
> That event has since been **deleted entirely** — producer, channel and topic — because moving
> revenue off it left it with no consumer at all. References to it here are the reasoning for the
> change, not a description of anything that still runs. `order-cancelled` is still produced.

---

## 1. Problem

The dashboard reports **orders placed** and calls it revenue.

`analytics-service` fills its fact table from `order-initiated`, which orders-service publishes at
**confirmation**. While no payment existed that was the furthest an order ever got, and
`docs/specs/order-lifecycle.md` section 6 says so explicitly: the figure is defensible only until
`PAID` exists. It exists now. Every confirmed order is counted as money taken, including ones whose
card was declined and which were cancelled seconds later.

The lifecycle spec calls this out as required work the moment payment lands, and the plan marks it
**must not lag phase 8**.

---

## 2. Goals and non-goals

**Goals**

- Revenue counts money actually taken.
- An order that was confirmed but never paid contributes nothing.
- The change is stated, not silent: a number that meant one thing yesterday means another today.

**Non-goals**

- Refunds. See section 6 - `REFUNDED` is currently unreachable in code, so there is nothing to
  reverse and nothing to test against. Building the refund flow is its own task.
- An "orders placed" funnel measure. See section 4.2; it is additive and nothing asks for it yet.
- Changing how line revenue is computed. The snapshot arithmetic is unchanged and correct.

---

## 3. The change

orders-service publishes **`order-paid`** when an order reaches `PAID`. analytics-service fills the
fact table from that event instead of `order-initiated`.

```
                        before                        after
  order-initiated  ->  fact_sales_line          (no longer ingested)
  order-paid           (does not exist)     ->  fact_sales_line
```

`order-paid` carries the same `OrderDTO` payload `order-initiated` does. That is deliberate: the
fact table needs the order's lines, prices and discounts, and re-sending them costs one message
rather than a lookup back into orders-service, which ADR-0010 rules out.

---

## 4. Why this shape

### 4.1 Why the state machine makes this simple

Three separate topics carry an order's story, and Kafka orders messages only within one topic's
partition. So in principle analytics can see `order-paid`, `order-initiated` and `order-cancelled`
in any order, and a design that maintained a per-order status in the warehouse would have to survive
every interleaving - including a redelivered `order-initiated` arriving after the payment and
quietly downgrading the row.

The lifecycle's transition table removes the problem rather than managing it. `CANCELLED` is
reachable from `INITIATED`, `CONFIRMED` and `RESERVED` - **never from `PAID`**. An order is
therefore either paid or cancelled, never both, and the two events never race for the same row:

- a cancelled order emits no `order-paid`, so there is no row to remove;
- a paid order emits no `order-cancelled`, so nothing arrives to remove it.

`order-cancelled` needs no consumer at all. That is worth stating plainly, because the plan lists
"`order-cancelled` reverses previously counted rows" as scope - and it is only scope while
`order-initiated` is what gets counted. Once revenue follows payment, the cancellation has nothing
to reverse.

### 4.2 Why `order-initiated` stops feeding the fact table

The plan allows either dropping it or keeping it as a separate "orders placed" measure. Dropping it:

- Nothing on the dashboard asks for orders-placed. It renders monthly revenue, per-product units and
  revenue, a category split and a total - all revenue-shaped.
- Keeping both in one table means a status column, which reintroduces exactly the cross-topic
  interleaving section 4.1 avoids.
- It stays additive. A funnel measure can be built later from the same stream without disturbing
  this.

### 4.3 Idempotency is unchanged

Ingestion still deletes by order id and re-inserts, and the fact table is still unique on
`(order_id, product_id)`. A redelivered `order-paid` rewrites the same rows rather than doubling
them. Nothing about at-least-once delivery changes.

---

## 5. Consequence: existing warehouses over-report

**Every row currently in `fact_sales_line` came from a confirmation, not a payment.** After this
change those rows stay, and they represent orders that may never have been paid for.

The warehouse is a read model and its rebuild path already exists - reset the consumer groups and
replay, as `analytics-service/CLAUDE.md` documents. The new group reads `order-paid` from the
earliest offset, so it backfills whatever the broker still holds.

Two things follow, and both are honest limitations rather than oversights:

- Replay only recovers what Kafka still retains. Orders paid before the retention window are gone.
- Before this change no `order-paid` was ever published, so **there is no history to backfill at
  all** on an existing environment. The first genuinely correct figure is the first order paid after
  this ships.

The rebuild must therefore also **clear the fact table**, not merely replay onto it - otherwise the
old confirmation-derived rows survive and are indistinguishable from real revenue.

---

## 6. Refunds, and why they are not here

The plan lists refunds as reversing counted rows. They cannot be, yet.

`REFUNDED` appears in `OrderStateMachine`'s transition table and nowhere else: no code moves an
order into it and no event announces it. payment-service had a `refund` method reachable only from
a command nothing sent, and it has since been removed - it wrote no reply, so an order could not
have left `PAID` even had the command arrived. There is no refund to observe, so a reversal path
here would be untestable code written against an imagined event.

When the refund flow is built it fits this design without changing it: full-order refunds only
(lifecycle spec section 10.5), so reversal is a delete by order id - which
`FactSalesLineRepository.deleteByOrderId` already does, and which section 4.3's idempotency already
relies on.

---

## 7. Failure-mode walkthrough

| Scenario | Behaviour |
|---|---|
| Order confirmed, never paid | Never counted. This is the whole point |
| Order confirmed, payment declined, cancelled | Never counted; no row to reverse |
| `order-paid` redelivered | Same rows rewritten, not doubled |
| `order-paid` arrives before its `order-initiated` | Irrelevant - `order-initiated` no longer touches the fact table |
| Order paid, then shipped/delivered | Counted once, at payment. Later states carry no revenue meaning |
| Warehouse rebuilt from empty | Backfills only orders paid since this shipped (section 5) |
| `order-paid` payload missing lines | Order contributes no rows, same as an order with no lines today |

---

## 8. Scope

**orders-service**
- Publish `order-paid` on the `PAID` transition, from the outbox, in the same transaction as the
  status write - the ADR-0002 rule every other saga message already follows
- Topic wiring: outbox relay route, emitter, channel config

**analytics-service**
- Consume `order-paid` into `fact_sales_line`
- Stop consuming `order-initiated` into it
- New consumer group, earliest offset, dead-letter queue - matching the existing three

**Docs**
- `docs/specs/analytics-dashboard.md`: its "only completed (confirmed) orders count as sales" bullet
  becomes wrong the moment this ships
- `backend/analytics-service/CLAUDE.md`: ingestion topic list and the replay runbook

---

## 9. Open questions

1. **Should the dashboard distinguish "no sales yet" from "warehouse not backfilled"?** After this
   ships an existing environment reports near-zero revenue until orders are paid, which looks
   identical to a broken ingestion. A note in the back-office would resolve it; whether it is worth
   building is a product call.
2. **Does anyone rely on the current figure?** If the dashboard's revenue number is already being
   read as real money, this change corrects it downward, possibly sharply. That is the correct
   number, but it should not be a surprise.

---

## 10. Convention compliance checklist

- [ ] Event published from the outbox in the same transaction as the status write (ADR-0002)
- [ ] Keyed by order id, like every other message concerning an order
- [ ] JSON: snake_case, nulls omitted, ISO-8601 dates, on the event as on HTTP
      (`json-serialization-conventions.md`)
- [ ] Consumer is idempotent; at-least-once delivery rewrites rather than doubles
- [ ] Dead-letter queue rather than blocking the partition, matching the existing consumers
- [ ] BCE layering: event payloads in `control/events/`, ingestion in `control/`
      (`architecture-conventions.md`)
- [ ] No new HTTP surface, so no OpenAPI change - the dashboard contract is untouched
