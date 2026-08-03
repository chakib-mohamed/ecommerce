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
capture-payment (Kafka)  ->  payment-service  ->  POST /charges (gateway HTTPS)
                                              <-  charge result
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

The buyer's card never touches the platform. The gateway's own client-side SDK collects it in the
browser and exchanges it for a **single-use token** directly with the gateway. Only that token
reaches us.

```
browser  --card details-->  gateway SDK  --token-->  browser
browser  --token----------> checkout                 (card data never enters our network)
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

## 7. Where the gateway lives

The gateway is external and configured, never embedded:

| Setting | Meaning |
|---|---|
| `payment.gateway.url` | base URL of the provider |
| `payment.gateway.api-key` | credential, supplied by the environment, never committed |
| `payment.gateway.timeout` | per-request timeout, shorter than the saga step deadline |

**Local and test environments** point at a stub that speaks the same HTTP contract - a container in
the Compose stack and a WireMock in tests. The stub exists so the platform can be run and tested
without a real merchant account; it is **not** a fallback, and no code path chooses between stub and
real gateway. The URL decides.

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

1. **Which gateway?** The HTTP contract, the idempotency-key header name, and the decline-reason
   vocabulary are all provider-specific. The stub can only be written once one is chosen.
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
