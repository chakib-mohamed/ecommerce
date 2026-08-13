# Spec: Order Fulfilment (ship & deliver)

**Status:** Draft — design spec for review (Spec gate, step 1 of the workflow).

Completes the tail of `docs/specs/order-lifecycle.md`. That spec defined `PAID → SHIPPED → DELIVERED`
and the state machine implements those transitions; nothing has ever been able to trigger them. This
specifies what does.

---

## 1. Problem

An order's last reachable state is `PAID`.

`OrderStateMachine` already allows `PAID → SHIPPED`, `SHIPPED → DELIVERED`, and treats `DELIVERED` as
terminal. `OrderStatus` carries both constants, the published OpenAPI enum lists them, the frontend
renders them, and products-service already counts them as purchased
(`OrdersApiClient.PURCHASED = [PAID, SHIPPED, DELIVERED]`), so a delivered order still entitles its
buyer to review the product.

Every part of the system is ready for these two states except the one that produces them. They are
**unreachable, not unused** — the lifecycle was specified whole and built as far as payment.

The consequences today are small but real:

- An order that has physically arrived still reads `PAID` to the buyer, which is a statement about
  money, not about a parcel.
- `SHIPPED → REFUNDED` is a legal transition out of a state nothing can enter, so a whole branch of
  the state machine is dead.
- `POST /orders/{orderID}/cancel` promises it "stops an order that has not yet shipped" — a
  distinction that currently cannot arise, because no order ever ships.

---

## 2. Goals and non-goals

**Goals**

- Two endpoints that move an order to `SHIPPED` and to `DELIVERED`.
- **Operator-authorized**, not buyer-authorized: dispatching a parcel is a warehouse fact, not
  something the buyer asserts about their own order.
- Roles carried in the access token, so "operator" is an identity the platform can actually check.

**Non-goals**

- Carrier integration, tracking numbers, shipping labels, delivery estimates, or webhooks from a
  logistics provider. These endpoints record that fulfilment happened; they do not perform it.
- Partial or split shipment. `docs/specs/order-lifecycle.md` §10.4 settled this: an order ships once,
  completely, with no per-line state.
- Returns and refunds. `REFUNDED` stays unreachable, exactly as `docs/specs/payment.md` §2 says.
  This spec makes `SHIPPED → REFUNDED` a transition out of a *reachable* state, which is a change in
  how visible that gap is, not a change in the gap itself. See §9.
- A fulfilment service, a warehouse queue, or any new deployable. Two endpoints on the service that
  already owns the aggregate.
- An admin UI. The endpoints are the deliverable; whatever calls them is a later question.

---

## 3. The endpoints

| Endpoint | Moves | Who |
|---|---|---|
| `POST /orders/{orderID}/ship` | `PAID → SHIPPED` | administrator |
| `POST /orders/{orderID}/deliver` | `SHIPPED → DELIVERED` | administrator |

Both return the updated order, and both take no body — there is nothing to supply. Neither reads the
order's owner, because neither is the owner's action.

Responses:

| Code | Meaning |
|---|---|
| `200` | transition applied; the updated order is returned |
| `401` | no token, or an expired one |
| `403` | the caller is not an administrator |
| `404` | no such order |
| `409` | the order is not in a state this transition can leave |

`409` is the state machine's own answer, reached through `assertCanTransition`, so these endpoints add
no second opinion about what is legal. An already-shipped order asked to ship again gets `409`, the
same as a double confirm — see §5 for why that is the right answer rather than a silent success.

---

## 4. Authorization: roles have to reach the token first

**This is the part of the work that is not in orders-service.**

`User` (authenticate-service) already carries `roles`, and the seed data sets
`admin@ecommerce.test → ["admin"]`, `retail@ecommerce.test → ["customer"]`. Those roles are used at
login and then dropped: `TokenUtils.generateToken` sets only `setSubject(email)`. No `groups` claim
is minted, so no service has ever seen a role, which is why every resource in the platform is
`@Authenticated` and not one uses `@RolesAllowed`.

`@RolesAllowed("admin")` on these endpoints would therefore reject **everyone, including the
administrator** — silently and correctly, since the token asserts no such thing.

**The change:** `TokenUtils.generateToken` takes the user's roles and mints them as the standard
MicroProfile JWT `groups` claim. `groups` specifically, because that is the claim
`@RolesAllowed` reads; a differently-named claim would parse fine and authorize nothing.

### 4.1 Blast radius

This edits the token contract, so it is worth being precise about who notices:

| Touched | Effect |
|---|---|
| `TokenUtils` | mints `groups`; needs the roles, so its signature changes from `String subject` |
| `AuthenticationResource` | passes `user.getRoles()` instead of only the email |
| Every service verifying the JWT | **no change** — an unrecognised claim is simply available, not required |
| Existing tokens | keep working; they carry no `groups`, so they authorize nothing new |
| Tests minting their own tokens | need `groups` to exercise the admin path |
| e2e | needs an admin session alongside the per-spec buyers |

A user with no roles, or a null roles list, mints no `groups` claim rather than an empty one. Both
authorize nothing; the distinction matters only in that an empty array in a token invites the reader
to think roles were considered and came back empty, which is not what happened.

### 4.2 What this deliberately does not become

Not a permissions system. One claim, one role name checked in two places. No role hierarchy, no
per-endpoint permission model, no admin management API. The `customer` role is minted for the same
reason `admin` is — it is on the user — and nothing checks it.

---

## 5. Idempotency and concurrency

A shipping tool is exactly the kind of caller that retries, so this needs an answer.

**Neither endpoint is idempotent, and that is the intended behaviour.** Shipping an order that has
already shipped is `409`, not `200`. The reasoning is the same as for double confirm: the second call
is a statement that turned out not to be true, and answering `200` would tell the caller it had just
dispatched something that was dispatched an hour ago.

**Concurrency** is handled the way every other transition in this service is: the read and the write
are not atomic, so the status is re-checked inside the conditional write. Two simultaneous ship
calls end with one `200` and one `409`, never two transitions.

---

## 6. Events: none

**No `order-shipped` or `order-delivered` event is emitted.**

Nothing consumes one. Analytics counts revenue at `PAID` (`docs/specs/order-lifecycle.md` §6) and
shipping moves no money; products-service already treats `PAID`, `SHIPPED` and `DELIVERED`
identically for review eligibility, so no read model diverges when the status changes.

Emitting an event nobody reads is precisely the situation `order-initiated` was in, and it was
deleted for it: it cost the publish, looked like a working integration, and delivered nothing. Adding
two more of the same on the grounds that a consumer might appear would be repeating a mistake this
repository has already paid to fix once.

If fulfilment ever needs to notify anything — a shipping confirmation email is the obvious candidate
— the event gets added *with* its consumer, in the task that needs it.

Note the asymmetry this leaves: `order-cancelled` currently has three producers and no consumer. That
is the same orphan situation and it is called out in the lifecycle spec, but it is pre-existing and
out of scope here.

---

## 7. Metrics

Two counters, following `docs/specs/functional-metrics.md`:

| Metric | Meaning |
|---|---|
| `orders.shipped` | orders dispatched |
| `orders.delivered` | orders confirmed delivered |

Counted only on a transition that actually committed, so a `409` increments nothing. The gap between
`orders.paid` and `orders.shipped` is the fulfilment backlog, which is the number worth watching.

---

## 8. Failure-mode walkthrough

| Scenario | Behaviour |
|---|---|
| Ship an order still `RESERVED` (payment not captured) | `409`. Stock is held but no money has been taken — dispatching would give the goods away |
| Ship an already-`SHIPPED` order | `409`; nothing transitions, no counter moves |
| Deliver an order that never shipped | `409`; `PAID → DELIVERED` is not in the table |
| Two ship calls at once | One `200`, one `409` — the conditional write decides |
| Ship a `CANCELLED` order | `409`; `CANCELLED` is terminal |
| Buyer calls ship on their own order | `403` — being the owner is not the permission being asked for |
| Administrator ships someone else's order | `200` — that is the whole point of the role |
| Buyer cancels while an operator ships | Whichever conditional write lands first wins; the loser gets `409`. Cancel-after-ship is already refused by the table |

---

## 9. Open questions

1. **Does `SHIPPED → REFUNDED` stay in the table?** It is legal in the state machine and unreachable
   in practice, because refunds are a stated non-goal. Once orders can actually reach `SHIPPED`, this
   is a legal transition sitting one implementation away from being callable, rather than two. Either
   it stays as documented intent or it comes out until refunds exist. **Not decided here** — it is
   the refund decision, not the fulfilment one.
2. **Should `DELIVERED` be operator-asserted at all?** In a system with a carrier integration,
   delivery is reported by the carrier, not typed by a human. This spec makes it an operator action
   because there is no carrier; if one is ever added, `deliver` becomes a webhook consumer and this
   endpoint's audience changes.

---

## 10. Convention compliance checklist

To be checked off after implementation, naming what enforces each.

- [ ] BCE layering: transitions in `control/`, endpoints in `boundary/` (`architecture-conventions.md`)
      — `BceArchitectureTest`
- [ ] Blocking JAX-RS and synchronous Panache only (`architecture-conventions.md`)
- [ ] JSON: snake_case, nulls omitted, ISO-8601 dates (`json-serialization-conventions.md`)
- [ ] Illegal transitions raise `IllegalOrderTransitionException` and map to `409`
      (`exception-handling-conventions.md`)
- [ ] No network I/O inside a transaction (`persistence-conventions.md`) — `TransactionalRulesArchTest`
- [ ] Logging: no token, no card data, order id and status on every transition
      (`logging-conventions.md`)
- [ ] Both `openapi.yaml` copies byte-identical, and the `OrdersApi` interface matches them
- [ ] No new Compose service, so none of the lists in the root `CLAUDE.md` need editing — the
      gateway's existing `Path=/api/orders/**` route already covers both endpoints
