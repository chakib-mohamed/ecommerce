# Payment

**Status:** Draft - design spec for review (Spec gate, step 1 of the workflow).

Phase 8 of `docs/tasks/order-lifecycle-plan.md`. Depends on phase 7, which built the saga and its
stock step. This specifies the participant that step hands off to.

---

## 1. Problem

The order lifecycle reaches `RESERVED` and stops. Nothing charges the buyer, so nothing can move an
order to `PAID`, and `docs/specs/order-lifecycle.md` section 6 warns that analytics counts a
confirmed order as revenue - defensible only while no payment exists.

There is no payment participant today. The saga diagram in the lifecycle spec (section 4.1) draws
one, but the reactor has no such module and Compose has no such service.

---

## 2. Goals and non-goals

**Goals**

- A `payment-service` that takes the saga's `capture-payment` command and answers it.
- Charging happens at an **external payment gateway**. This service delegates; it does not
  implement card processing, hold balances, or decide whether a card is good for the money.
- **No card data in the platform, ever** - not in a request, a database, a log, or a Kafka message.

**Non-goals**

- Storing, transmitting or seeing a PAN, expiry or CVV. This is the point of the design, not a
  simplification of it.
- Multiple gateways or gateway failover. One configured gateway.
- Partial captures, instalments, or authorise-now-capture-later. One capture per order.
- Stored cards or repeat billing. Each order carries its own token.

---

## 3. What the service is

A thin adapter between the saga and the gateway. Its whole job:

```
capture-payment (Kafka)  ->  payment-service  ->  POST /v1/payment_intents (Stripe HTTPS)
                                              <-  intent result
payment-captured / payment-failed (Kafka)  <-
```

It owns one thing the gateway cannot: the record of **which order a charge belongs to**, and which
saga step asked for it. That record is what makes redelivery safe.

### 3.1 What it stores

| Field | Why |
|---|---|
| `orderId` | what the charge is for |
| `stepId` | which saga attempt asked; the idempotency key |
| `providerRef` | the gateway's own identifier, for refunds and reconciliation |
| `status` | `CAPTURED`, `FAILED`, `REFUNDED` |
| `amount`, `currency` | what was charged, for reconciliation against the order |
| `failureReason` | shown to the buyer when a charge is declined |

**Not stored, at any point:** card number, expiry, CVV, cardholder name, or any value derived from
them. The service never receives them, so it cannot store them by accident.

---

## 4. How card data stays out

The buyer's card never touches the platform. Stripe.js collects it in the browser and exchanges it
for a **payment method reference** (`pm_...`) directly with Stripe. Only that reference reaches us.

```
browser  --card details-->  Stripe.js  --pm_xxx-->  browser
browser  --pm_xxx---------> checkout                (card data never enters our network)
```

The token is an opaque string. The platform treats it as meaningless: it is passed to the gateway
with the capture and is never parsed, logged, or persisted beyond the life of the capture attempt.

**Consequences that constrain the implementation:**

- The token arrives on the confirm request and travels to payment-service in the `capture-payment`
  command. It is **not** written to the order aggregate, and **not** stored on the payment record
  after the capture resolves.
- Tokens are single-use and short-lived at the gateway. A capture retried long after the token was
  issued will be refused by the gateway, which is correct: the saga's step deadline should be
  shorter than the token's lifetime, or a slow saga fails at the gateway rather than at the deadline.
- Nothing in `logging-conventions.md`'s sensitive-data rules needs an exception, because there is no
  sensitive data in this service to redact.

---

## 5. Idempotency

Delivery is at-least-once in both directions, and a redelivered capture that charged twice would
take money twice. Three mechanisms, in order of precedence:

1. **The gateway's idempotency key is the saga `stepId`.** Every real gateway honours one. A
   redelivered capture reaches the gateway with the same key and returns the *original* charge
   rather than making a second one. This is the guarantee that actually protects the buyer.
2. **The local record is keyed by `(orderId, stepId)`.** A redelivered command that finds an
   existing record replies with the recorded outcome without calling the gateway at all.
3. **The orchestrator discards stale replies** by step id and legal transition, as phase 7 already
   does for stock.

Mechanism 2 is an optimisation; mechanism 1 is the correctness argument. If the local record is lost
the gateway still refuses to double-charge.

---

## 6. Events

| Event | Direction | Carries |
|---|---|---|
| `capture-payment` | orders -> payment | `orderId`, `stepId`, `amount`, `currency`, `paymentToken` |
| `payment-captured` | payment -> orders | `orderId`, `stepId`, `providerRef` |
| `payment-failed` | payment -> orders | `orderId`, `stepId`, `reason` |
| `refund-payment` | orders -> payment | `orderId`, `stepId` |

Keyed by `orderId`, like every other saga message, so one order's messages keep their order.

The reply is written to payment-service's outbox **in the same transaction as the payment record**,
for the reason ADR-0002 exists: a charge that committed while its reply was lost would leave an order
waiting on money that had already been taken.

---

## 7. The gateway: Stripe

**Stripe**, via the Payment Intents API. One capture is one intent, created and confirmed in a
single call.

| Call | Purpose |
|---|---|
| `POST /v1/payment_intents` | capture, with `confirm=true` |
| `POST /v1/refunds` | refund, by `payment_intent` |

| Setting | Meaning |
|---|---|
| `payment.stripe.url` | API base URL; the mock in local and test, `api.stripe.com` elsewhere |
| `payment.stripe.api-key` | secret key, supplied by the environment, never committed |
| `payment.stripe.timeout` | per-request timeout, shorter than the saga step deadline |

### 7.1 Two Stripe details that constrain the code

**Amounts are integer minor units.** Stripe takes `amount: 1050` for 10.50, not a decimal. The
platform holds money as `BigDecimal` at scale 2 (phase 5), so the client converts at the boundary
with `movePointRight(2).longValueExact()` - and `longValueExact` deliberately, because a value
carrying more than two decimals is a bug that must throw rather than round silently on its way to a
real charge.

**The idempotency key is a header**, `Idempotency-Key`, carrying the saga `stepId`. Stripe stores
the first response against that key and replays it for 24 hours. The saga step deadline must stay
well inside that window, or a retry after expiry would create a second intent - a second charge.

### 7.2 Mocking it locally

`stripe/stripe-mock` runs as a container in the Compose stack, and the same image backs the
integration tests through Testcontainers. `payment.stripe.url` points at it. It is **not** a
fallback: no code path chooses between mock and Stripe, the URL decides, and the client is identical
either way.

**What the mock is good for, and what it is not.** `stripe-mock` validates requests against Stripe's
published OpenAPI spec and returns canned responses from it. That makes it a genuine check that our
requests are *shaped* the way Stripe expects - wrong field name, wrong amount type, missing
parameter all fail against it.

It is stateless. It does not track idempotency keys, does not decline test cards, and does not
remember an intent between calls. So it cannot exercise the behaviour section 8 cares about most:
declines, timeouts, and whether a redelivered capture charges twice. **Those paths are tested against
WireMock**, where the response can be programmed per test.

Both are needed and they test different things: `stripe-mock` proves we speak Stripe's dialect,
WireMock proves we handle what Stripe says back.

---

## 8. Failure-mode walkthrough

| Scenario | Behaviour |
|---|---|
| Gateway declines the card | `payment-failed` with the decline reason; saga releases stock, order `CANCELLED` |
| Gateway times out, charge never made | Step deadline fires, saga compensates, order `CANCELLED`. Retry is safe: the idempotency key means a late-arriving charge is the same charge |
| Gateway times out, charge **was** made | The worst case. The saga cancels and releases stock, but money was taken. Reconciliation is by `providerRef`; **this needs an alert, not silence** - see section 9 |
| Reply lost after capture | Redelivered command finds the local record, replies with the original outcome; no second charge |
| Capture redelivered | Gateway returns the original charge for the same idempotency key |
| Token already used or expired | Gateway refuses; treated as a decline |
| Refund after `PAID` | `refund-payment` to the gateway by `providerRef`; stock restocked; order `REFUNDED` |

---

## 9. Open questions

These need answering before implementation, not during.

1. **Which decline reasons are shown to the buyer?** Stripe returns a `decline_code` from a long
   vocabulary (`insufficient_funds`, `lost_card`, `do_not_honor`, ...). Some are safe to show and
   some are deliberately vague for fraud reasons. The mapping is a business decision.
2. **Who resolves a timed-out-but-charged capture?** Section 8's worst case leaves money taken
   against a cancelled order. An automated refund sweep is possible but risky; an operator queue is
   safer and slower. This is a business decision.
3. **Does the frontend collect the token?** This spec assumes it does. No frontend work is planned,
   so until that lands there is no source of real tokens and the flow is only exercisable against
   the stub.
4. **Where does the token enter the API?** It has to reach `POST /orders/{id}/confirm`, which
   currently takes no body. That is an OpenAPI change and needs gate 2 before any code.

---

## 10. Convention compliance checklist

- [ ] BCE layering: gateway client and capture logic in `control/`, DTOs in `boundary/dto/`, event
      payloads in `control/events/` (`architecture-conventions.md`)
- [ ] Blocking JAX-RS and synchronous Panache only; no reactive stack
      (`architecture-conventions.md`)
- [ ] JSON: snake_case, nulls omitted, ISO-8601 dates, on HTTP and on events
      (`json-serialization-conventions.md`)
- [ ] Exceptions: declines are `FunctionalException` with an `errorCode`; gateway faults propagate as
      technical errors (`exception-handling-conventions.md`)
- [ ] No network I/O inside a transaction - the gateway call happens outside, and only its result
      enters one (`persistence-conventions.md`)
- [ ] Reply written to the outbox in the same transaction as the payment record (ADR-0002)
- [ ] Logging: no token, no `providerRef` at INFO, no amount without currency
      (`logging-conventions.md`)
- [ ] New service wired into Compose, the CI service mapping, and the observability stack
