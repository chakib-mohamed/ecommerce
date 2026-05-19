# Implementation Plan: Pricing Event Propagation

**Spec:** `docs/specs/pricing-event-propagation.md`
**OpenAPI:** `docs/specs/pricing-event-propagation-openapi.yaml`
**Failing tests:** confirmed across 3 services — ready to implement.

---

## Architecture Summary

```
PUT /api/prices/{productId}       (price-service)
  → validates price > 0
  → upserts Price entity in MongoDB
  → publishes PriceChangedEvent to Kafka topic "price-changed"

products-service (Kafka consumer)
  → PriceChangedConsumer.consume(event)
  → ProductService.updatePrice(productId, newPrice)
  → updates Product.price in PostgreSQL

orders-service (cache + BPMN engine)
  → PriceCacheService.getProduct(productId) — Redis TTL cache of ProductDto
  → on miss: calls ProductsApiClient.getProduct()
  → PricingApiClient calls POST /pricing/calculate on price-service
  → price-service runs ApplyPromotionsService (step 1) + Drools rules (step 2)
  → returns PriceCalculationResponse { id, order }
```

---

## What Already Exists (No Creation Needed)

- `price-service`: `Price` entity, `PriceChangedEvent` DTO, `ApplyPromotionsService`, `ApplySpecialOffers.drl`, `price.bpmn2`, all 4 DTOs, `PriceResource`/`PricingResource` stubs, `MongoDbTestResource`, test `application.properties`
- `products-service`: `PriceChangedConsumer` stub (no `@Incoming`), `PriceChangedEvent` DTO, `ProductService.updatePrice` stub (empty body), Kafka dep (`quarkus-messaging-kafka`), `MinioTestResource`
- `orders-service`: `PriceCacheService.getProduct` stub (returns null), `ProductsApiClient`, `PricingApiClient` at `@Path("/pricing")`, `RedisTestResource`, `MongoTestResource`

---

## Dependency Graph

```
[A] price-service pom.xml — add Kafka + Drools deps
    ↓
[B] FunctionalException + GlobalExceptionHandler + InvalidPriceException
    ↓
[C] KafkaPriceEventPublisher
    ↓
[D] PriceService (validate, upsert MongoDB, fire CDI event)
    ↓
[E] PriceResource.updatePrice (inject PriceService, return PriceResponse)
    ↓
CHECKPOINT A: PriceResourceTest (3 tests) passes

[F] kmodule.xml + InvalidOrderException
    ↓
[G] PricingService (chain ApplyPromotionsService + KieSession Drools)
    ↓
[H] PricingResource.calculatePrice (inject PricingService)
    ↓
CHECKPOINT B: PricingResourceTest (4 tests) passes
              Full price-service test suite passes

[I] ProductService.updatePrice body (find by UUID, update price)
    ↓
[J] PriceChangedConsumer: add @Incoming + call updatePrice
    ↓
[K] products-service application.properties: Kafka consumer config + test devservices off
    ↓
CHECKPOINT C: PriceChangedConsumerTest (1 test) passes
CHECKPOINT D: All products-service tests pass (no regressions)

[L] PriceCacheService.getProduct body (Redis check → API call → cache)
    ↓
[M] PricingApiClient: change @Path to /pricing/calculate
    ↓
CHECKPOINT E: PriceCacheServiceTest (2 tests) passes
              All orders-service tests pass (no regressions)

[N] API Gateway: add /api/prices/** route
    ↓
CHECKPOINT F: Full build (./mvnw clean package -DskipTests) succeeds
```

---

## Phase 1: price-service — Kafka + PriceResource

### Task 1.1 — pom.xml: add Kafka and Drools dependencies

Add to `backend/price-service/pom.xml`:
- `quarkus-smallrye-reactive-messaging-kafka`
- `kie-api:7.53.1.Final`, `drools-core:7.53.1.Final`, `drools-compiler:7.53.1.Final`

Verify: `./mvnw clean package -DskipTests -pl price-service` compiles.

### Task 1.2 — Exception infrastructure

Create:
- `the.chak.ecommerce.pricing.control.FunctionalException` (abstract, carries `Response.Status`)
- `the.chak.ecommerce.pricing.control.InvalidPriceException` (extends `FunctionalException`, status 400)
- `the.chak.ecommerce.pricing.boundary.GlobalExceptionHandler` (`@ServerExceptionMapper` pattern)

### Task 1.3 — KafkaPriceEventPublisher

New file: `the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher`

Pattern:
```java
@ApplicationScoped
public class KafkaPriceEventPublisher {
    @Inject @Channel("price-changed") Emitter<PriceChangedEvent> emitter;

    void onPriceChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) PriceChangedEvent event) {
        emitter.send(event);
    }
}
```

### Task 1.4 — PriceService

New file: `the.chak.ecommerce.pricing.control.PriceService`

Responsibilities: validate price > 0 (throw `InvalidPriceException`), upsert `Price` entity in MongoDB, fire `Event<PriceChangedEvent>`.

### Task 1.5 — Implement PriceResource.updatePrice

Inject `PriceService`. Call `priceService.update(productId, request.getPrice())`. Return `Response.ok(new PriceResponse(...)).build()`. No try-catch.

### Task 1.6 — Kafka + test properties for price-service

Add to `src/main/resources/application.properties`:
```
mp.messaging.outgoing.price-changed.connector=smallrye-kafka
mp.messaging.outgoing.price-changed.topic=price-changed
mp.messaging.outgoing.price-changed.value.serializer=io.quarkus.kafka.client.serialization.JsonbSerializer
```

Add to `src/test/resources/application.properties` (mock the emitter in tests via `@InjectMock KafkaPriceEventPublisher` in test class, OR configure in-memory):
```
%test.mp.messaging.outgoing.price-changed.connector=smallrye-in-memory
```

**CHECKPOINT A:** `./mvnw test -pl price-service -Dtest=PriceResourceTest` → 3/3 pass.

---

## Phase 2: price-service — PricingResource (BPMN + Drools)

### Task 2.1 — kmodule.xml

Create `src/main/resources/META-INF/kmodule.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<kmodule xmlns="http://www.drools.org/xsd/kmodule">
  <kbase name="pricing-rules" packages="the.chak.pricing">
    <ksession name="pricing-session"/>
  </kbase>
</kmodule>
```

### Task 2.2 — InvalidOrderException

New: `the.chak.ecommerce.pricing.control.InvalidOrderException` (extends `FunctionalException`, status 400).

### Task 2.3 — PricingService

New file: `the.chak.ecommerce.pricing.control.PricingService`

Execution chain:
1. Validate: `order != null && !order.getProducts().isEmpty()`, else throw `InvalidOrderException`
2. Call `applyPromotionsService.applyPromotion(order)` — applies `percentageOff` discount
3. Open `KieSession` from classpath container, insert `order` and each `ProductVO`, fire all rules (`ruleflow-group = "ApplySpecialOffers"`)
4. After rules: re-sum product prices into `order.setPrice(total)`
5. Return `new PriceCalculationResponse(UUID.randomUUID().toString(), order)`

Price computation:
- Qty=6, price=10.0, no promo → step 1: unchanged → step 2 (qty>5): `price * 0.95 = 9.5` per item × 6 = `57.0`
- Qty=1, price=100.0, promo=10% → step 1: `100.0 * 0.90 = 90.0` → step 2 (qty≤5): no rule fires → total `90.0`

### Task 2.4 — Implement PricingResource.calculatePrice

Inject `PricingService`. Call `pricingService.calculate(request)`. Return `Response.ok(response).build()`.

**CHECKPOINT B:** `./mvnw test -pl price-service` → all 7 tests pass.

---

## Phase 3: products-service — Kafka Consumer

### Task 3.1 — Implement ProductService.updatePrice

```java
@Transactional
public void updatePrice(String productId, Double newPrice) {
    Product product = Product.<Product>find("uuid", UUID.fromString(productId)).firstResult();
    if (product != null) {
        product.setPrice(newPrice);
    }
}
```

Within a `@Transactional` boundary, the managed entity is auto-dirty-tracked — no explicit merge needed.

### Task 3.2 — Wire PriceChangedConsumer

Add `@Incoming("price-changed")` to `consume()` method. Add body: `productService.updatePrice(event.getProductId(), event.getNewPrice());`.

### Task 3.3 — Kafka and test configuration

Add to `src/main/resources/application.properties`:
```
mp.messaging.incoming.price-changed.connector=smallrye-kafka
mp.messaging.incoming.price-changed.topic=price-changed
mp.messaging.incoming.price-changed.value.deserializer=io.quarkus.kafka.client.serialization.JsonbDeserializer
```

Add/fix `src/test/resources/application.properties`:
```
%test.quarkus.devservices.enabled=false
%test.mp.messaging.incoming.price-changed.connector=smallrye-in-memory
%test.mp.messaging.incoming.price-changed.merge=true
%test.mp.messaging.outgoing.product-updated.connector=smallrye-in-memory
%test.mp.messaging.outgoing.product-deleted.connector=smallrye-in-memory
```

**CHECKPOINT C:** `./mvnw test -pl products-service -Dtest=PriceChangedConsumerTest` → 1/1 passes.
**CHECKPOINT D:** `./mvnw test -pl products-service` → all 7 tests pass.

---

## Phase 4: orders-service — Cache + Client

### Task 4.1 — Implement PriceCacheService.getProduct

```java
public ProductDto getProduct(String productId) {
    String key = "product:" + productId;
    ProductDto cached = productValues.get(key);
    if (cached != null) {
        return cached;
    }
    ProductDto product = productsApiClient.getProduct(productId);
    if (product == null) {
        return null;
    }
    productValues.setex(key, (long) ttlMinutes * 60, product);
    return product;
}
```

`productValues` is already `ValueCommands<String, ProductDto>` initialized in `@PostConstruct`.

### Task 4.2 — Fix PricingApiClient path

Change `@Path("/pricing")` on the interface to `@Path("/pricing/calculate")`. The `@POST` method keeps no additional `@Path`. Existing `OrdersResourceTest` mocks this client via `@InjectMock @RestClient PricingApiClient` — no regression expected.

**CHECKPOINT E:** `./mvnw test -pl orders-service` → all 24 tests pass.

---

## Phase 5: API Gateway

### Task 5.1 — Add /api/prices/** route

In `backend/ecommerce-api-gateway/src/main/resources/application.yml`, add:
```yaml
- id: price-service
  uri: http://price-service:8080
  predicates:
    - Path=/api/prices/**
  filters:
    - StripPrefix=1
```

Place before the existing `payments-service` entry (also pointing to `price-service:8080`).

**CHECKPOINT F:** `./mvnw clean package -DskipTests` from `backend/` builds all modules.

---

## Known Risks

| Risk | Mitigation |
|---|---|
| Drools classpath scanning fails (wrong package in kmodule.xml) | Match `packages="the.chak.pricing"` exactly — that's the DRL `package` declaration |
| `ProductDto` JSON-B serialization in Redis fails (byte[] image field) | Image is null in test ProductDto — null fields serialize cleanly |
| BPMN package mismatch in price.bpmn2 | Not using BPMN engine — calling ApplyPromotionsService directly in Java |
| products-service Kafka startup failure in tests without broker | Use `smallrye-in-memory` connector for all channels in test profile |
| PricingApiClient path change breaks OrdersResourceTest | OrdersResourceTest mocks the client — path change doesn't affect mock behavior |
