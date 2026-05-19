# Spec: Price-Event Propagation

## Objective

Decouple base price management from the cart/order flows, while keeping price-service as the sole engine for effective price calculation (BPMN + Drools). This preserves price-service's ability to evolve independently (e.g. AI-based pricing) without touching orders-service.

**Current state:**
- `price-service` has a BPMN process and Drools rules but no REST endpoint for price calculation — `PricingApiClient` (`POST /pricing`) is a dead call pointing at a non-existent resource.
- `orders-service` caches only `Double` prices fetched from `products-service`.
- There is no way to set a base product price — no admin API exists.

**Target state (Option A — services remain separate):**
- `price-service` adds two responsibilities:
  1. **Base price storage:** `PUT /prices/{productId}` stores the new price in MongoDB and publishes `PriceChangedEvent` to Kafka.
  2. **Effective price calculation:** `POST /pricing/calculate` runs the BPMN process (promotions + Drools qty rules) on an `OrderDTO` and returns the computed total + process ID.
- `products-service` consumes `price-changed` events and updates `Product.price` in PostgreSQL — single source of truth for product data including current base price.
- `orders-service` keeps `PricingApiClient` for effective price calculation at order creation. `PriceCacheService` is refactored to cache full `ProductDto` objects instead of bare `Double` prices.

**Users:** internal services; no direct end-user API surface changes.

---

## Affected Services

| Service | Change type |
|---|---|
| `price-service` | Add price storage, price update endpoint, Kafka producer |
| `products-service` | Add Kafka consumer for `price-changed` |
| `orders-service` | Refactor `PriceCacheService` (cache `ProductDto`), remove `PricingApiClient` from `OrderService` |

---

## Event Contract

**Topic:** `price-changed`  
**Serializer:** `io.quarkus.kafka.client.serialization.JsonbSerializer` (matches existing project convention)

```json
{
  "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "newPrice": 49.99
}
```

The `PriceChangedEvent` DTO is kept independent in each service (no new shared API module). Both producer and consumer define the same two-field class.

---

## Tech Stack

All services: Quarkus 3.17.6, Java 21.

| Service | New dependency |
|---|---|
| `price-service` | `io.quarkus:quarkus-smallrye-reactive-messaging-kafka`, `io.quarkus:quarkus-mongodb-panache` (for `Price` entity) |
| `products-service` | No new dependency (already has `quarkus-smallrye-reactive-messaging-kafka`) |
| `orders-service` | No new dependency |

---

## Commands

```bash
# Build a single service
./mvnw clean package -DskipTests -pl price-service       # from backend/
./mvnw clean package -DskipTests -pl products-service
./mvnw clean package -DskipTests -pl orders-service

# Test a single service
./mvnw test -pl price-service
./mvnw test -pl products-service
./mvnw test -pl orders-service

# Full stack
./build.sh
./run.sh docker
```

---

## Project Structure

### price-service — new files

```
boundary/
  PriceResource.java                    # PUT /prices/{productId}
  PricingResource.java                  # POST /pricing/calculate (replaces dead PricingApiClient target)
  dto/
    UpdatePriceRequest.java             # { Double price }
    PriceResponse.java                  # { String productId, Double price }
control/
  PriceService.java                     # stores price, fires Kafka event
  PricingService.java                   # orchestrates BPMN process execution
  KafkaPriceEventPublisher.java         # @Channel("price-changed") Emitter
  events/
    PriceChangedEvent.java              # { String productId, Double newPrice }
entity/
  Price.java                            # PanacheMongoEntity { String productId, Double price }
```

### products-service — new files

```
control/
  PriceChangedConsumer.java             # @Incoming("price-changed") consumer
  events/
    PriceChangedEvent.java              # same DTO, independent copy
```

### orders-service — modified files

```
control/
  PriceCacheService.java                # ValueCommands<String, ProductDto> (was Double)
  OrderService.java                     # remove PricingApiClient; compute total from product prices
```

### orders-service — no deletions

`PricingApiClient` is kept. Its `@Path` changes from `/pricing` to `/pricing/calculate` to match the new endpoint.

---

## Code Style

Follow the existing patterns in the codebase:

**Kafka consumer (matches `featured-products-service` pattern):**
```java
@ApplicationScoped
public class PriceChangedConsumer {

    @Inject
    ProductService productService;

    @Incoming("price-changed")
    public void consume(PriceChangedEvent event) {
        productService.updatePrice(event.getProductId(), event.getNewPrice());
    }
}
```

**Kafka producer (matches `products-service` KafkaEventPublisher pattern):**
```java
@ApplicationScoped
public class KafkaPriceEventPublisher {

    @Inject
    @Channel("price-changed")
    Emitter<PriceChangedEvent> emitter;

    public void onPriceChanged(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) PriceChangedEvent event) {
        emitter.send(event);
    }
}
```

**PriceCacheService — change cache type:**
```java
// Before
private ValueCommands<String, Double> values;
values = redis.value(Double.class);
values.setex("price:" + productId, ttl, product.getPrice());

// After
private ValueCommands<String, ProductDto> values;
values = redis.value(ProductDto.class);
values.setex("product:" + productId, ttl, product);
```

**Order total computation (replaces PricingApiClient call):**
```java
double total = order.getProducts().stream()
    .mapToDouble(p -> priceCacheService.getProduct(p.getProductID()).getPrice() * p.getQty())
    .sum();
order.setPrice(total);
```

**Exception handling:** follow the two-tier model — `PriceResource` throws `FunctionalException` subtypes; no try-catch in resource classes.

---

## Testing Strategy

Framework: JUnit 5 + REST Assured + Testcontainers. All tests use explicit containers; Dev Services are always disabled.

### price-service tests

| Test class | What it covers |
|---|---|
| `PriceResourceTest` (`@QuarkusTest`) | `PUT /prices/{productId}` returns 200; subsequent GET returns updated price; unknown product ID returns 404 |
| `KafkaPriceEventPublisherTest` | Verifies `PriceChangedEvent` is emitted after a successful price update (use `@InjectMock` on the Emitter or an in-memory channel via `@TestChannel`) |

Testcontainers needed: `MongoDbTestResource` (same pattern as `authenticate-service`).

### products-service tests

| Test class | What it covers |
|---|---|
| `PriceChangedConsumerTest` (`@QuarkusTest`) | Consuming a `PriceChangedEvent` updates `Product.price` in PostgreSQL |

Testcontainers needed: existing `PostgreSQLContainer` + `KafkaContainer` (or in-memory channel).

### orders-service tests

| Test class | What it covers |
|---|---|
| `PriceCacheServiceTest` | Cache miss calls `products-service`, caches full `ProductDto`; cache hit returns cached product without calling API |
| `OrderServiceTest` | `saveOrder` computes total from product prices (no `PricingApiClient` call); verify `PricingApiClient` is NOT invoked |

---

## Boundaries

- **Always:** use `JsonbSerializer` for Kafka producers (Jakarta JSON-B, not Jackson); disable Dev Services in test properties; write failing tests before implementing; follow the `FunctionalException` pattern.
- **Ask first:** changing the `ProductDto` structure in `products-api` (shared module — affects all consumers); adding a new shared API module for the event DTO; changing the `price-changed` topic name.
- **Never:** remove `PricingApiClient` from `orders-service` — it is the intentional synchronous coupling for BPMN/rule-based calculation; store card payment fields in the event.

---

## Success Criteria

1. `PUT /prices/{productId}` in `price-service` stores the new price in MongoDB and returns `200`.
2. A `PriceChangedEvent` is published to the `price-changed` Kafka topic within the same transaction scope.
3. `products-service` consumes the event and updates `Product.price` in PostgreSQL. A `GET /products/{uuid}` after the event is processed returns the new price.
4. `orders-service` `PriceCacheService` stores `ProductDto` (not `Double`) in Redis under key `product:{productId}`.
5. `OrderService.saveOrder` computes the order total from `ProductDto.price × qty`; `PricingApiClient` is never called.
6. All existing cart and order tests continue to pass.
7. All new tests pass with Testcontainers (no Dev Services).

---

## Open Questions

None — all questions resolved before writing this spec.
