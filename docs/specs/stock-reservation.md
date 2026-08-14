# Spec: Stock Reservation

**Status:** Implemented. Written after the fact, to document a saga participant that existed only in
code.

The saga's stock step, in `products-service`. This is the counterpart to `docs/specs/payment.md`:
both are participants the orchestrator commands over Kafka, and until now only one of them was
specified. `docs/specs/order-lifecycle.md` §4 describes this step from the orchestrator's side, and
ADR-0011 records why the saga is orchestrated at all — neither says what the participant guarantees.

---

## 1. Problem

Nothing read `stock` before this existed. The catalog carried the number, mappers exposed it, and no
code path ever consulted it before accepting an order — so the same unit could be sold to every buyer
who asked. Overselling was not a race that needed unlucky timing; it was the normal case.

---

## 2. Goals and non-goals

**Goals**

- Make a unit unavailable once somebody is buying it, and available again when they stop.
- Answer the saga's `reserve-stock` command with a decision the orchestrator can act on.
- Survive at-least-once delivery in both directions without ever decrementing twice.

**Non-goals**

- Partial fulfilment. An order is held completely or not at all — see §4.
- Reservation expiry. Held stock is returned only by an explicit release (§6).
- Backorders, waitlists, or per-warehouse stock. One number per product.
- Reserving without an order. Every reservation is keyed by an order id.

---

## 3. What it stores

One `StockReservation` row per order line, in the products Postgres database:

| Field | Why |
|---|---|
| `orderId` | what the hold is for, and the key everything is done by |
| `productId`, `quantity` | what to give back, without re-reading the order |
| `status` | `HELD`, `RELEASED`, `REJECTED` |
| `createdAt` | when the hold was taken |

**`REJECTED` rows are kept deliberately.** They are what makes a redelivered `reserve-stock` answer
the same way twice — see §5. A schema that only recorded successes would have to re-decide a
redelivered command against stock that has since moved, and could contradict a reply the orchestrator
already acted on.

---

## 4. All-or-nothing

A reservation holds every line of an order or none of them, because the lifecycle has no state for a
partly filled order: either it can be met and moves to `RESERVED`, or it cannot and is cancelled.

The implementation detail worth knowing, because it looks wrong at a glance:

```java
// on a line that cannot be met
taken.forEach(t -> productRepository.incrementStock(t.productId(), t.quantity()));
lines.forEach(l -> record(orderId, l, ReservationStatus.REJECTED));
return false;
```

Lines already taken are given back **by hand rather than by rolling the transaction back**. A
rollback would be the obvious way to undo them, and it would also discard the `REJECTED` rows — which
are exactly what has to survive for the redelivery guarantee in §5 to hold. So the transaction
commits, and the undo is explicit.

---

## 5. Idempotency

Delivery is at-least-once, and a redelivered command that decremented twice would take stock nobody
is buying.

**Reserve** short-circuits on any existing row for the order and reports the *original* outcome:

```java
if (!existing.isEmpty()) {
    boolean held = existing.stream().anyMatch(r -> r.getStatus() != ReservationStatus.REJECTED);
    return held;                      // never re-tested against current stock
}
```

**Release** touches only `HELD` rows, so a repeat finds nothing and does nothing. An unknown order is
not an error: releasing after a rejected reservation is a legitimate saga path, and so is a
redelivered compensation.

**Release is keyed by order id alone — the `stepId` on the command is ignored.** That is what makes a
release safe to issue from any step, including one that never took anything, and it is why
`OrderService.cancelOrder` and `SagaDeadlineSweep` can both send one without knowing which step held
the stock. Any future change that made release step-sensitive would break both callers silently.

---

## 6. No expiry, and what that obliges

Reservations carry no TTL. `docs/specs/order-lifecycle.md` §10.3 settled this: a reservation is held
by a saga that always terminates, so the deadline sweep is the mechanism that frees stock, not a
timer on the reservation.

**The obligation this creates is the important part.** Nothing in `products-service` ever returns
held stock on its own, so **every path in `orders-service` that leaves `RESERVED` while holding stock
must emit `release-stock`**. That is not a style preference; it is the only mechanism. Two defects
have already come from forgetting it:

- `OrderService.cancelOrder` cancelled a reserved order and released nothing. The deadline sweep then
  found an order it could no longer cancel, took the branch written for shipped orders, cleared the
  deadline and released nothing — where the leak became permanent. Fixed; see `OrderCancellationTest`.
- `InitiatedOrderExpirySweep` was nearly written with the same shape. Its guard is `status !=
  INITIATED` rather than `stateMachine.canTransition(status, CANCELLED)` **because the latter answers
  yes for `RESERVED`**, which would have cancelled an order holding stock while issuing no release.

---

## 7. Concurrency

The decrement is a single conditional UPDATE, not a read followed by a write:

```java
update("stock = stock - ?1 where uuid = ?2 and stock >= ?1", quantity, uuid) == 1
```

Two orders racing for the last unit both attempt it; the database serialises them, and the loser
matches no row and is rejected. No pessimistic lock, no version column, and no window in which both
succeed.

---

## 8. Events

| Event | Direction | Carries |
|---|---|---|
| `reserve-stock` | orders → products | `orderId`, `stepId`, lines |
| `stock-reserved` | products → orders | `orderId`, `stepId` |
| `stock-rejected` | products → orders | `orderId`, `stepId`, reason |
| `release-stock` | orders → products | `orderId`, `stepId` (ignored — see §5) |

Keyed by `orderId`, like every other saga message, so one order's messages keep their order. Replies
are written to the products outbox in the same transaction as the reservation, for the reason ADR-0002
exists: stock held while its reply was lost would leave an order waiting on a decision already made.

`release-stock` has no reply. The orchestrator is already on its way to `CANCELLED` and there is no
state for "failed to release" — a lost release is recovered by redelivery, not by a reply.

---

## 9. Failure-mode walkthrough

| Scenario | Behaviour |
|---|---|
| One line of several cannot be met | Whole order rejected; lines already taken are restored by hand; `REJECTED` rows recorded |
| `reserve-stock` redelivered | Original outcome replayed from the stored rows; stock untouched |
| `release-stock` redelivered | Only `HELD` rows are released, so the repeat finds nothing |
| Release for an order that was rejected | No-op, and legitimate — the saga does this |
| Two orders race for the last unit | The conditional decrement decides; one is rejected |
| Reply lost after stock was held | Redelivered command replays the original answer; no second decrement |
| Order cancelled while holding stock, no release sent | **Stock is leaked permanently.** Nothing expires a reservation — see §6 |

---

## 10. Convention compliance checklist

- [x] BCE layering: reservation logic in `control/`, entity in `entity/`, command payloads in
      `control/events/` (`architecture-conventions.md`) — enforced by `BceArchitectureTest`
- [x] Blocking JAX-RS and synchronous Panache only (`architecture-conventions.md`)
- [x] `@Transactional` on the control-layer methods that mutate, never on a resource
      (`persistence-conventions.md`) — enforced by `TransactionalRulesArchTest`
- [x] Reply written to the outbox in the same transaction as the reservation (ADR-0002)
- [x] JSON: snake_case, nulls omitted, on events as well as HTTP
      (`json-serialization-conventions.md`) — pinned by the shared saga contract fixtures
- [x] Logging: order id and line count on every decision, no payload dumps
      (`logging-conventions.md`)
