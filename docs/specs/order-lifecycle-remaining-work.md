# Spec: Order Lifecycle — Remaining Work

**Status:** Open. Nothing here is implemented.

The cross-service concerns the order lifecycle leaves open, ordered by criticality. This is a
standing record rather than a feature spec: it describes what is *not* built and what has to be
decided before it can be, so that each item can be picked up cold. Items 1 and 3 will each want
their own feature spec when they are started; the rest are decisions or cleanups that do not.

Each is its own branch and its own session, per the task-session policy in the root `CLAUDE.md`.

The work that precedes this — `feature/order-lifecycle-spec`, PR #21 — is complete on its own terms:
the state machine, the saga, payment, fulfilment and the cancellation fix each went spec → contract →
failing tests → implementation. Nothing below is an unfinished piece of that work. Each is either a
capability that was never started or a decision that was deliberately left to the owner.

Related specs: `order-lifecycle.md`, `payment.md`, `order-fulfilment.md`, `analytics-revenue.md`.

---

## What merging PR #21 makes true

Worth stating once, because three items below are consequences of that merge rather than
pre-existing faults:

- **Money can now be taken.** Before it, no capture existed. `payment-service` and the saga's
  capture step arrive with it.
- **`status_reason` is now buyer-visible** and carries whatever the gateway said.
- **Nothing deploys from `main`.** The `kubernetes/` manifests are stale and unwired, and
  `payment.stripe.url` points at `stripe-mock` in every environment that exists. So none of this is
  in front of real cards today — which is what makes it safe to merge and fix in sequence.

---

## 1. Refunds — `feature/order-refunds`

**The only gap where the platform can take a customer's money and offer no way to return it.**

`REFUNDED` exists in `OrderStatus` (both copies), in the transition table as `PAID → REFUNDED` and
`SHIPPED → REFUNDED`, and in the published OpenAPI enum. There is no refund code anywhere — verified
by grep: outside enums and `OrderStateMachine`, nothing references it. `docs/specs/payment.md` §2
makes refunds an explicit non-goal and records that a half-built refund path was removed rather than
left looking finished.

**Why this is first.** `docs/specs/payment.md` §8's worst case — gateway times out, the charge *was*
made, the saga cancels and releases stock — leaves money taken against a cancelled order. The
`PaymentGatewayFault` alert (`observability/rules/order-lifecycle.rules.yml`) fires, so it is not
silent. But nothing can act on it: every remedy is a Stripe dashboard visit plus a hand-edited order.

**Scope**

- A refund saga step and its command/reply pair, mirroring `capture-payment`.
- `POST /v1/refunds` at Stripe, keyed by the saga `stepId` like the capture is.
- `providerRef` is already recorded on the payment record for exactly this.
- Reversal in the analytics warehouse. **This is the part that is not obvious**: the warehouse only
  ever grows. `docs/specs/analytics-revenue.md` §4.1 argues `order-cancelled` needs no consumer
  because a cancelled order was never counted — that argument does not extend to refunds, which
  reverse revenue that *was* counted. `IngestionService` already deletes by order id, which is the
  hook.
- Making `REFUNDED` reachable retires items 3 and 4 below.

**Decisions to take first**

- Full-order only? `docs/specs/order-lifecycle.md` §10.5 says yes, and that partial refunds would
  make the status ambiguous. Confirm before building.
- Who may trigger one — almost certainly `@RolesAllowed("admin")`, which now works: the `groups`
  claim landed with PR #21 and `e2e/specs/fulfilment.spec.ts` proves it end to end.

---

## 2. Who owns a timed-out-but-charged capture — decision, then runbook

`docs/specs/payment.md` §9.2, unanswered. The alert exists and has no defined response: no queue, no
owner, no runbook. An alert nobody is assigned to is a notification, not a control.

Inseparable from item 1 — even a named owner has no refund to issue until it exists. Sequence this
immediately after, and land the runbook with it rather than as a separate task.

---

## 3. Buyer-facing decline reasons — `fix/payment-decline-reasons`

**Smallest item on this list, and the only one that is an exposure rather than a missing capability.
Do it first if anything real ever points at `api.stripe.com`.**

`SagaService.onPaymentFailed` writes the provider's `reason` straight onto `order.statusReason`,
which is published on `OrderDTO`. Stripe's `decline_code` vocabulary includes values that are
deliberately vague for fraud reasons — `lost_card`, `stolen_card`, `do_not_honor` — and relaying them
verbatim tells a card holder more than the issuer intended. `docs/specs/payment.md` §9.1 flagged the
mapping as a business decision; it now has somewhere to be read from, so the decision is live.

**Scope:** a mapping from gateway code to buyer-safe text, applied at the boundary where the reason
becomes buyer-visible. Keep the raw code for operators — at DEBUG or on the payment record, never on
a published event. An hour of work plus its tests.

---

## 4. Cancelling from `RESERVED` — decision

Currently **refused** with `409`. `OrderService.cancelOrder` rejects it explicitly; the transition
stays legal in the state machine because the saga itself cancels from `RESERVED` on a decline, and
that path knows the money was *not* taken.

The reasoning is recorded in `docs/specs/order-lifecycle.md` §3.2 and holds only while refunds do not
exist: `RESERVED` means the capture was already commanded, so a buyer cancelling there always races a
charge in flight, and releasing the stock while it lands would take money for an order that no longer
exists to pay for. Revisit as part of item 1 — it is the same decision, not a separate one.

---

## 5. `PAID`/`SHIPPED` → `REFUNDED` dead branches — decision

Legal transitions into an unreachable state, and more visible since PR #21 made `SHIPPED` genuinely
reachable. Either implement item 1 or drop them from the table; leaving a transition that nothing can
trigger invites someone to assume it works. Folded into item 1 if that happens first.

---

## 6. Purchase verification timing out reads as `500` — `fix/review-verification-timeout`

A timed-out purchase check surfaces as a server error rather than a refusal. It sits on a path real
users hit — reviewing a product — and a `500` tells the browser something broke when in fact the
answer is merely unknown.

**This is a contract change**, so it needs gate 2 before any code: today a caller can distinguish
"you did not buy this" (`403`) from "we could not tell" (`500`), and collapsing them loses that. The
question is which is the better lie when the truth is unavailable.

Related and already done, so do not redo it: the client timeouts on that path were widened to 5s and
the smoke gate warms the call before the suite runs.

---

## 7. e2e stability — `chore/e2e-stability`

**2 clean runs out of 12** across the life of PR #21. Not stable, and it should not be described as
stable until several consecutive runs are clean.

Fixed during PR #21, each with its own root cause:

- per-spec buyers, because an order history is shared mutable state under `fullyParallel`;
- a readiness gate, because Compose reports healthy long before the stack can sell;
- the lost-confirm test, which asserted the outcome of a race it starts;
- the admin accordion, whose control does not exist until a second render — `open` starts `null` and
  an effect opens the first category once the catalog loads. **One clean run so far**; three or four
  consecutive would settle it.

**Two wrong diagnoses are recorded here on purpose**, so they are not tried again: the admin flake is
*not* the server being slow to answer `/api/categories` (the smoke gate proves it answers and the
flake continued), and it is *not* fixed by adding an earlier assertion (`setCats` and `setOpen` are
in the same effect, so React batches them and no earlier element resolves sooner).

Remaining work is watching, not patching. Resist a fifth change until several runs say one is needed.

---

## 8. No fulfilment UI — `feature/admin-fulfilment-ui`

`POST /orders/{id}/ship` and `/deliver` exist, are routed, are role-guarded and are covered end to
end — and nothing in the front end calls them. A deliberate non-goal in
`docs/specs/order-fulfilment.md` §2, which means fulfilment is not usable by a human yet.

Note also that `POST /orders/{id}/cancel` is not wired either: the Orders page's "Cancel order"
button calls `deleteOrder`, and only for `INITIATED`. That is why the cancellation defects fixed in
PR #21 were latent rather than observed.

---

## 9. Stale Kubernetes manifests — `chore/kubernetes-manifests`

`kubernetes/` references a removed `eureka-server` and is not wired into the Makefile. Already
flagged in the root `CLAUDE.md` as a separate follow-up. Lowest priority — nothing depends on it, and
nothing deploys from it.

---

## Not on this list, and why

- **`order-cancelled` has no consumer.** It is now emitted by every path that reaches `CANCELLED`,
  including the buyer's own, so the stream is complete. Deleting it would remove information rather
  than noise, and a notification or funnel feature would want exactly it. Keep, do not delete —
  unlike `order-initiated`, which was superseded by `order-paid` and removed for that reason.
- **`SHIPPED`/`DELIVERED` unreachable.** Closed by PR #21.
- **The buyer-cancellation defects** — leaked stock, the erasable write, the missing event. Fixed in
  PR #21, covered by `OrderCancellationTest`.
