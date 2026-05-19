# Todo: Pricing Event Propagation Implementation

## Phase 1 — price-service: Kafka + PriceResource

- [ ] **1.1** `price-service/pom.xml` — add `quarkus-smallrye-reactive-messaging-kafka`, `kie-api`, `drools-core`, `drools-compiler` (v7.53.1.Final)
  - Verify: `./mvnw clean package -DskipTests -pl price-service` compiles
- [ ] **1.2** Create `FunctionalException` (abstract base, carries `Response.Status`)
  - File: `the.chak.ecommerce.pricing.control.FunctionalException`
- [ ] **1.3** Create `InvalidPriceException extends FunctionalException` (status 400)
  - File: `the.chak.ecommerce.pricing.control.InvalidPriceException`
- [ ] **1.4** Create `GlobalExceptionHandler` (`@ServerExceptionMapper` for `FunctionalException`)
  - File: `the.chak.ecommerce.pricing.boundary.GlobalExceptionHandler`
- [ ] **1.5** Create `KafkaPriceEventPublisher` (`@Observes(AFTER_SUCCESS)` CDI → `Emitter`)
  - File: `the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher`
- [ ] **1.6** Create `PriceService` (validate, upsert `Price` in MongoDB, fire CDI `Event<PriceChangedEvent>`)
  - File: `the.chak.ecommerce.pricing.control.PriceService`
- [ ] **1.7** Implement `PriceResource.updatePrice` (inject `PriceService`, return `PriceResponse`)
- [ ] **1.8** Add Kafka outgoing config to `price-service/src/main/resources/application.properties`
- [ ] **1.9** Add `%test.mp.messaging.outgoing.price-changed.connector=smallrye-in-memory` to `price-service/src/test/resources/application.properties`

**CHECKPOINT A:** `./mvnw test -pl price-service -Dtest=PriceResourceTest` → 3/3 pass

---

## Phase 2 — price-service: PricingResource (Drools)

- [ ] **2.1** Create `src/main/resources/META-INF/kmodule.xml` with `<kbase packages="the.chak.pricing">`
- [ ] **2.2** Create `InvalidOrderException extends FunctionalException` (status 400)
  - File: `the.chak.ecommerce.pricing.control.InvalidOrderException`
- [ ] **2.3** Create `PricingService` (validate → `ApplyPromotionsService.applyPromotion()` → `KieSession` Drools rules → sum prices → return `PriceCalculationResponse`)
  - File: `the.chak.ecommerce.pricing.control.PricingService`
- [ ] **2.4** Implement `PricingResource.calculatePrice` (inject `PricingService`, return `PriceCalculationResponse`)

**CHECKPOINT B:** `./mvnw test -pl price-service` → all 7 tests pass

---

## Phase 3 — products-service: Kafka Consumer

- [ ] **3.1** Implement `ProductService.updatePrice(productId, newPrice)` — find by UUID, set price, let transaction flush
- [ ] **3.2** Add `@Incoming("price-changed")` + body to `PriceChangedConsumer.consume()`
- [ ] **3.3** Add incoming Kafka config to `products-service/src/main/resources/application.properties`
- [ ] **3.4** Add/update `products-service/src/test/resources/application.properties` — disable devservices, use `smallrye-in-memory` for all channels in test profile

**CHECKPOINT C:** `./mvnw test -pl products-service -Dtest=PriceChangedConsumerTest` → 1/1 passes
**CHECKPOINT D:** `./mvnw test -pl products-service` → all 7 tests pass

---

## Phase 4 — orders-service: Cache + Client

- [ ] **4.1** Implement `PriceCacheService.getProduct(productId)` — Redis lookup → API call → cache with TTL
- [ ] **4.2** Change `PricingApiClient @Path` from `/pricing` to `/pricing/calculate`

**CHECKPOINT E:** `./mvnw test -pl orders-service` → all 24 tests pass

---

## Phase 5 — API Gateway

- [ ] **5.1** Add `/api/prices/**` → `price-service:8080` route to `ecommerce-api-gateway/src/main/resources/application.yml`

**CHECKPOINT F:** `./mvnw clean package -DskipTests` from `backend/` builds all modules
