# Code Review: `price-service`

> Reviewed: 2026-05-20

## Bugs

### 1. DRL rule imports wrong package — facts never match at runtime
**File:** `src/main/resources/rules/ApplySpecialOffers.drl:1-3`
**Severity:** Bug

```drl
import the.chak.ecommerce.orders.entity.OrderDTO;
import the.chak.ecommerce.orders.entity.ProductVO;
```

`OrderDTO` and `ProductVO` live in `the.chak.ecommerce.orders.boundary.dto`, not `.entity`. Drools resolves imports at session creation time; with the wrong package, no facts will ever match the rule. The `PricingService` inserts `OrderDTO` and iterates `ProductVO` lists — none will trigger the rule. The 5 % bulk discount is silently never applied. Fix the imports to match the actual class locations.

---

### 2. DRL bulk-discount rule fires once per matching product, re-applying discount to all
**File:** `src/main/resources/rules/ApplySpecialOffers.drl`
**Severity:** Bug

The rule's consequence iterates `order.getProducts()` and applies the 5 % discount to every product with `qty > 5`. If two products both have `qty > 5`, the rule fires twice (once for each matching `ProductVO` in working memory), and each firing re-iterates all products and re-applies the discount to every qualifying item. With two qualifying products, each gets discounted twice. Fix: use a single rule with `accumulate` or `collect`, or restructure so each product is discounted exactly once (e.g., fire the rule per product and discount only `$p`, not all products in `order`).

---

### 3. `ApplyPromotionsService` mutates `productVO.price` to the line total
**File:** `src/main/java/the/chak/ecommerce/price/control/ApplyPromotionsService.java`
**Severity:** Bug

```java
productVO.setPrice(productVO.getPrice() * productVO.getQuantity());
```

This overwrites the unit price with the line total. Downstream code (the DRL rule, the response serialiser) that reads `productVO.getPrice()` after `applyPromotion` is called now sees a total, not a unit price. The semantics of the field change mid-call without any documentation. Either compute the total in a separate field or rename to make the mutation explicit.

---

### 4. `UpdatePriceRequest.price` has no Bean Validation
**File:** `src/main/java/the/chak/ecommerce/price/boundary/dto/UpdatePriceRequest.java`
**Severity:** Bug

No `@NotNull` or `@Positive` constraint on the `price` field. A `null` or negative price can be persisted without error. Add `@NotNull @Positive(message = "price must be positive") BigDecimal price` and enable `@Valid` on the resource method.

---

### 5. `javax.inject.Singleton` wrong namespace in `application.properties`
**File:** `src/main/resources/application.properties:7`
**Severity:** Bug

```properties
javax.inject.Singleton=true
```

Quarkus 3.x uses the `jakarta.*` namespace exclusively. The `javax.inject.Singleton` property has no effect. This was presumably intended to control CDI scope behaviour or suppress a warning. Remove it or replace with the correct `jakarta.*` form if a legitimate need exists.

---

## Security

### 6. `PUT /prices/{productId}` has no authentication
**File:** `src/main/java/the/chak/ecommerce/price/boundary/PriceResource.java`
**Severity:** High

The price-update endpoint is unauthenticated. Any caller who can reach the service (or bypass the gateway) can overwrite prices for any product. Add `@Authenticated` (or a role check) consistent with the rest of the platform.

---

### 7. `POST /pricing/calculate` has no authentication
**File:** `src/main/java/the/chak/ecommerce/price/boundary/PricingResource.java`
**Severity:** High

The pricing-calculation endpoint accepts a full `OrderDTO` and applies Drools rules against it. It is unauthenticated, so any external caller can probe the pricing engine, enumerate discount thresholds, or trigger Drools rule evaluation. Add `@Authenticated`.

---

## Architecture

### 8. Banned `quarkus-rest` (reactive REST) in use
**File:** `pom.xml:42`
**Severity:** High

`quarkus-rest` is the reactive REST extension, explicitly banned by `backend/CLAUDE.md`. Replace with `quarkus-resteasy` and `quarkus-resteasy-jsonb`.

---

### 9. Drools 7.x is EOL and incompatible with GraalVM native builds
**File:** `pom.xml`
**Severity:** High

Drools 7.53.1.Final reached end-of-life. It has no GraalVM native support, so `./mvnw package -Pnative` will fail for this service. If native packaging is ever required, migrate to Drools 8.x (Kogito-based) or replace the rules engine with plain Java logic.

---

### 10. Deprecated SmallRye `@Channel`/`Emitter` instead of MicroProfile standard
**File:** `src/main/java/the/chak/ecommerce/price/control/KafkaPriceEventPublisher.java`
**Severity:** Medium

```java
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
```

These are the old SmallRye-specific annotations, superseded by `org.eclipse.microprofile.reactive.messaging.Channel` and `org.eclipse.microprofile.reactive.messaging.Emitter` in MicroProfile Reactive Messaging 2.0. The SmallRye ones are deprecated and may be removed in a future Quarkus release. Other services already use the MicroProfile annotations — align.

---

### 11. `Emitter.send()` result is not handled — Kafka failures are silent
**File:** `src/main/java/the/chak/ecommerce/price/control/KafkaPriceEventPublisher.java`
**Severity:** Medium

`emitter.send(event)` returns a `CompletionStage<Void>`. The return value is discarded. If the Kafka broker is unavailable or the message is rejected, the failure is silently swallowed — the price is updated in MongoDB but the downstream `price-changed` event is never delivered. Either chain `.exceptionally(...)` on the returned stage or use `@Blocking` + `emitter.sendAndAwait()`.

---

### 12. Kafka publish is not atomic with MongoDB write
**File:** `src/main/java/the/chak/ecommerce/price/control/PriceService.java`
**Severity:** Medium

MongoDB is updated first; the Kafka event is fired afterwards. If the process crashes between the two calls, the price is updated in the database but no `price-changed` event is published. Downstream services (notably `products-service` `PriceChangedConsumer`) never learn of the update. Consider the outbox pattern or at-least-once retry logic.

---

### 13. New `KieSession` created per pricing request
**File:** `src/main/java/the/chak/ecommerce/price/control/PricingService.java`
**Severity:** Medium

A new `KieSession` (stateful) is created for every call to `calculatePrice`. `KieSession` creation is expensive — it clones the working memory and agenda. For stateless rule evaluation (one shot per request), use `StatelessKieSession` instead; it is designed for this pattern and avoids the lifecycle overhead.

---

### 14. `Price.productId` has no unique MongoDB index — concurrent writes produce duplicates
**File:** `src/main/java/the/chak/ecommerce/price/entity/Price.java`
**Severity:** Medium

The upsert logic in `PriceService` finds an existing `Price` by `productId` before deciding to update or insert. Under concurrent requests, two threads can both find no existing document and both insert — leaving duplicate prices for the same product. `findByProductId` then returns `firstResult()`, silently hiding the second. Add a unique index on `productId`.

---

### 15. `InstanceHealthCheckService` is a dead Eureka stub
**File:** `src/main/java/io/quarkus/eureka/registration/InstanceHealthCheckService.java`
**Severity:** Smell

This class lives in the `io.quarkus.eureka.registration` package — a namespace that belongs to the removed `quarkus-eureka` extension. It is a stub left over from when service discovery was Eureka-based. Delete it; it serves no purpose and pollutes the package tree with a misleading package name.

---

### 16. `HealthCheckController` duplicates SmallRye Health
**File:** `src/main/java/the/chak/ecommerce/price/boundary/HealthCheckController.java`
**Severity:** Smell

A manual `/info/health` endpoint that returns a hardcoded string duplicates what SmallRye Health already exposes at `/q/health`. The `/info/status` endpoint returns an empty `{}`. Delete this controller; the platform gateway should probe the standard SmallRye health endpoints.

---

## Readability / Code Quality

### 17. `GlobalExceptionHandler.handleUnexpected` does not log the exception
**File:** `src/main/java/the/chak/ecommerce/price/boundary/GlobalExceptionHandler.java`
**Severity:** Medium

```java
@ServerExceptionMapper
public Response handleUnexpected(Exception e) {
    return Response.serverError().entity(...).build();
}
```

The caught exception is swallowed without a `log.error(...)` call. Unexpected 500s will have no stack trace in the logs, making production debugging impossible. Add `log.error("Unexpected error", e)` before building the response.

---

### 18. `System.out.println` debug statements in DRL file
**File:** `src/main/resources/rules/ApplySpecialOffers.drl`
**Severity:** Medium

Two `System.out.println(...)` calls are present in the rule consequence block. These write to stdout in production. Remove them.

---

### 19. `new DecimalFormat` per `applyPromotion` call — not thread-safe
**File:** `src/main/java/the/chak/ecommerce/price/control/ApplyPromotionsService.java`
**Severity:** Style

`DecimalFormat` is not thread-safe. Creating a new instance per call sidesteps the thread-safety issue but is wasteful. Declare it as a static constant and document that it must not be shared across threads, or use `String.format("%.2f", value)` instead.

---

## Tests

### 20. `PriceResourceTest` and `PricingResourceTest` are well-structured
**File:** `src/test/java/the/chak/ecommerce/price/boundary/`
**Severity:** Positive note

Both test classes use `@QuarkusTestResource` with explicit `MongoDbTestResource` and `KafkaTestResource` — the correct pattern. Dev Services is disabled. This is the best-tested service reviewed so far; no structural test issues found.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug | `ApplySpecialOffers.drl:1` | Wrong package imports — DRL facts never match; discount never applied |
| 2 | Bug | `ApplySpecialOffers.drl` | Rule fires once per matching product, re-applying discount to all |
| 3 | Bug | `ApplyPromotionsService.java` | `productVO.price` mutated to line total, corrupting unit-price semantics |
| 4 | Bug | `UpdatePriceRequest.java` | `price` field has no `@NotNull`/`@Positive` — null/negative prices accepted |
| 5 | Bug | `application.properties:7` | `javax.inject.Singleton` wrong namespace — has no effect in Quarkus 3.x |
| 6 | High Security | `PriceResource.java` | `PUT /prices/{productId}` unauthenticated |
| 7 | High Security | `PricingResource.java` | `POST /pricing/calculate` unauthenticated |
| 8 | High Arch | `pom.xml:42` | Banned `quarkus-rest` (reactive) in use |
| 9 | High Arch | `pom.xml` | Drools 7.x is EOL and incompatible with native builds |
| 10 | Medium Arch | `KafkaPriceEventPublisher.java` | Deprecated SmallRye `@Channel`/`Emitter` — use MicroProfile equivalents |
| 11 | Medium Arch | `KafkaPriceEventPublisher.java` | `emitter.send()` result discarded — Kafka failures are silent |
| 12 | Medium Arch | `PriceService.java` | Kafka publish not atomic with MongoDB write |
| 13 | Medium Arch | `PricingService.java` | New `KieSession` per request — use `StatelessKieSession` |
| 14 | Medium Arch | `Price.java` | No unique index on `productId` — concurrent inserts produce duplicates |
| 15 | Smell | `InstanceHealthCheckService.java` | Dead Eureka stub in wrong package — delete |
| 16 | Smell | `HealthCheckController.java` | Manual health endpoint duplicates SmallRye Health |
| 17 | Medium | `GlobalExceptionHandler.java` | `handleUnexpected` swallows exception without logging |
| 18 | Medium | `ApplySpecialOffers.drl` | `System.out.println` debug statements in production rule file |
| 19 | Style | `ApplyPromotionsService.java` | `new DecimalFormat` per call — wasteful; use `String.format` instead |
| 20 | Positive | Test classes | `@QuarkusTestResource` used correctly — best test coverage of all services |
