# Code Review: `orders-service`

> Reviewed: 2026-05-20

## Bugs

### 1. `processID` is set after `persist()` and is never saved to MongoDB
**File:** `src/main/java/the/chak/ecommerce/orders/control/OrderService.java:66-68`  
**Severity:** Bug

```java
order.persist();
order.setProcessID(response.getId());   // too late — already written to DB
```

`processID` is set on the in-memory object after the MongoDB write. No subsequent `order.update()` is called, so `processID` is always `null` in the database. Move `setProcessID` before `persist()`.

---

### 2. NPE in `isPromotionActive` when promotion dates are null
**File:** `src/main/java/the/chak/ecommerce/orders/control/OrderService.java:83-85`  
**Severity:** Bug

```java
return promotion.getActiveFrom().isBefore(now) && now.isBefore(promotion.getActiveTo());
```

`activeFrom` and `activeTo` are not declared `NOT NULL` anywhere. A promotion stored without dates causes a `NullPointerException` here during `saveOrder`, producing an unhandled 500. Add null guards before dereferencing.

---

### 3. `ProductsApiClient.getProduct` returns null on non-200, but callers don't null-check
**File:** `src/main/java/the/chak/ecommerce/orders/control/ProductsApiClient.java:27-29`, `OrderService.java:51`  
**Severity:** Bug

`getProduct` returns `null` when the products-service responds with anything other than 200. `OrderService.saveOrder` then calls `product.getTitle()` at line 51 without a null check — a 404 from products-service (unknown product UUID) throws NPE and fails the entire order creation with an unhandled 500.

---

### 4. `searchOrders` silently truncates count with `(int)` cast
**File:** `src/main/java/the/chak/ecommerce/orders/control/OrderService.java:103-104`  
**Severity:** Bug

```java
long totalCount = panacheQuery.count();
int count = (int) totalCount;
```

Silent integer overflow for stores with more than ~2.1 billion orders. Widen `Tuple<Integer, List<Order>>` to `Tuple<Long, List<Order>>` and propagate the change through the DTO and resource.

---

### 5. Kafka bootstrap hardcoded to `localhost:9092` — wrong for Docker
**File:** `src/main/resources/application.properties:34`  
**Severity:** Bug

```properties
mp.messaging.outgoing.order-initiated.bootstrap.servers=localhost:9092
```

This is a bare property with no profile prefix. In Docker Compose, `localhost:9092` resolves to the container's own loopback, not the Kafka broker. Use `%dev.kafka.bootstrap.servers` (already a convention in other services) and `%prod.` / Docker hostname for production.

---

### 6. `javax.inject.Singleton` in REST client scope — wrong Jakarta namespace
**File:** `src/main/resources/application.properties:26`  
**Severity:** Bug

```properties
the.chak.ecommerce.products.boundary.ProductsApi/mp-rest/scope=javax.inject.Singleton
```

Quarkus 3.x uses `jakarta.*`. The `javax.inject.Singleton` value is silently ignored or causes a class-not-found warning. Replace with `jakarta.inject.Singleton`.

---

## Security

### 7. `Order` entity stores card number and expiry in plaintext
**File:** `src/main/java/the/chak/ecommerce/orders/entity/Order.java:12,14`  
**Severity:** Critical

`cardNumber` and `expirationDate` are stored as plain strings in MongoDB. Storing primary account numbers (PANs) in cleartext violates PCI-DSS SAQ D. Card data must be tokenised through a PCI-compliant payment provider — the service should never store raw card numbers at all.

---

### 8. `OrdersResource` has no authentication or authorisation guard
**File:** `src/main/java/the/chak/ecommerce/orders/boundary/OrdersResource.java:15`  
**Severity:** High

`CartResource` has `@Authenticated` at the class level. `OrdersResource` has neither `@Authenticated` nor any role check. Any unauthenticated request that reaches this service can create, update, delete, or confirm any order. Add `@Authenticated` at minimum; add ownership checks on `updateOrder`, `deleteOrder`, and `confirmOrder` (verify the requesting user owns the order).

---

### 9. `deleteOrder` has no ownership check
**File:** `src/main/java/the/chak/ecommerce/orders/boundary/OrdersResource.java:46-51`  
**Severity:** High

Even once authentication is added, any authenticated user can delete any other user's order by guessing or enumerating MongoDB ObjectIds. The deleting user's identity should be verified against `order.userID` before deletion.

---

### 10. `orders-service` calls `price-service` via the API gateway
**File:** `src/main/resources/application.properties:29`  
**Severity:** Medium

```properties
the.chak.ecommerce.orders.control.PricingApiClient/mp-rest/url=http://api-gateway:8080/api/payments
```

Internal service-to-service communication should not go through the public-facing gateway. This creates unnecessary latency, requires the service to send a valid JWT for its own internal calls, and makes the pricing path dependent on gateway health. Point directly to `price-service:8080`.

---

## Architecture

### 11. Banned `quarkus-rest` (reactive REST) in use
**File:** `pom.xml:41`  
**Severity:** High

`quarkus-rest` is the reactive REST extension, explicitly banned by `backend/CLAUDE.md`. Replace with `quarkus-resteasy` and `quarkus-resteasy-jsonb`.

---

### 12. Both `quarkus-rest-jsonb` and `quarkus-rest-jackson` declared
**File:** `pom.xml:83, 114`  
**Severity:** Medium

Two competing JSON providers are on the classpath. Same dual-provider issue as authenticate-service. Pick one (JSON-B is used elsewhere) and remove the other.

---

### 13. `@LoggingFilter.Logged` on outgoing REST client is dead code
**File:** `src/main/java/the/chak/ecommerce/orders/control/PricingApiClient.java:15`, `LoggingFilter.java`  
**Severity:** Medium

`LoggingFilter` is a JAX-RS `ContainerRequestFilter` — it intercepts **incoming** requests to the server. Annotating an outgoing `@RegisterRestClient` interface method with `@Logged` has no effect; the filter never runs for outbound calls. Remove the annotation from `PricingApiClient` or replace with a proper `ClientRequestFilter` if outbound logging is needed.

---

### 14. `OrderService` injects `OrderMapper` — boundary concern in service layer
**File:** `src/main/java/the/chak/ecommerce/orders/control/OrderService.java:37-38`  
**Severity:** Smell

Same layering violation as in products-service: a boundary mapper is injected into the control layer. The `OrderMapper` call needed to build the `OrderDTO` for Kafka should be done in the resource or a dedicated publisher.

---

### 15. No atomicity between order creation and cart deletion on checkout
**File:** `src/main/java/the/chak/ecommerce/orders/control/CartService.java:109-111`  
**Severity:** Smell

`orderService.saveOrder(order)` and `cart.delete()` are two separate MongoDB operations with no transaction. If the node crashes between them, the order exists but the cart is not cleared. A MongoDB multi-document transaction (available since Mongo 4.0) would make this atomic.

---

### 16. `ErrorResponse` uses `errorCode` field instead of the project-standard `type`
**File:** `src/main/java/the/chak/ecommerce/orders/boundary/dto/ErrorResponse.java`  
**Severity:** Smell

`backend/CLAUDE.md` specifies `ErrorResponse { String type, String message }`. This service uses `{ String errorCode, String message }`, diverging from the convention.

---

## Readability / Code Quality

### 17. `LoggingFilter` double-logs every request
**File:** `src/main/java/the/chak/ecommerce/orders/control/LoggingFilter.java:36-37`  
**Severity:** Style

```java
System.out.println(String.format("Request %s %s from IP %s", method, path, address));
LOG.infof("Request %s %s from IP %s", method, path, address);
```

The same message is written to stdout via `System.out.println` and to the proper logger. Remove the `System.out.println`.

---

### 18. `CartService.toResponse` passes explicit `null, null` for prices
**File:** `src/main/java/the/chak/ecommerce/orders/control/CartService.java:116`  
**Severity:** Style

```java
new CartItemResponse(i.getProductId(), i.getQuantity(), null, null)
```

The split between `toResponse` (no prices) and `toResponseWithPrices` (with prices) duplicates the item-mapping logic. Unify into a single method that always calls `priceCacheService.getPrice` — `null` is already handled as "unavailable" in `toResponseWithPrices`.

---

## Tests

### 19. `OrdersResourceTest` has no `@BeforeEach` cleanup — tests share DB state
**File:** `src/test/java/the/chak/ecommerce/orders/boundary/OrdersResourceTest.java`  
**Severity:** Medium

`CartResourceTest` correctly calls `Cart.deleteAll()` in `@BeforeEach`. `OrdersResourceTest` has no equivalent cleanup. Tests create orders directly and then search for them by `userID`, but previous test runs' data can leak into `testSearchOrders`, making it non-deterministic (it may find orders from other tests with the same `userID`).

---

### 20. `PriceCacheServiceTest` misses the `getPrice` caching path
**File:** `src/test/java/the/chak/ecommerce/orders/control/PriceCacheServiceTest.java`  
**Severity:** Smell

The test covers `getProduct` caching but never tests `getPrice` — a separate Redis key path (`"price:" + productId`). The cache-miss→fetch→set→hit sequence for `getPrice` is untested.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug | `OrderService.java:67` | `processID` set after `persist()` — never written to DB |
| 2 | Bug | `OrderService.java:84` | NPE on null promotion `activeFrom`/`activeTo` |
| 3 | Bug | `ProductsApiClient.java:27` / `OrderService.java:51` | Null return on non-200 leads to NPE in caller |
| 4 | Bug | `OrderService.java:104` | `long → int` cast silently overflows for large count |
| 5 | Bug | `application.properties:34` | Kafka bootstrap hardcoded to `localhost:9092` — wrong for Docker |
| 6 | Bug | `application.properties:26` | `javax.inject.Singleton` — wrong Jakarta namespace |
| 7 | Critical Security | `Order.java:12,14` | Card number and expiry stored in plaintext |
| 8 | High Security | `OrdersResource.java:15` | No `@Authenticated` — all order endpoints are open |
| 9 | High Security | `OrdersResource.java:46` | `deleteOrder` has no ownership check |
| 10 | Medium Security | `application.properties:29` | Internal call routed through public API gateway |
| 11 | High Arch | `pom.xml:41` | Banned `quarkus-rest` (reactive) in use |
| 12 | Medium Arch | `pom.xml:83,114` | Dual JSON providers (`quarkus-rest-jsonb` + `quarkus-rest-jackson`) |
| 13 | Medium Arch | `PricingApiClient.java:15` | `@LoggingFilter.Logged` on outgoing client is dead code |
| 14 | Smell | `OrderService.java:37` | `OrderMapper` injected into service layer |
| 15 | Smell | `CartService.java:109` | No atomicity between order creation and cart deletion |
| 16 | Smell | `ErrorResponse.java` | Uses `errorCode` instead of project-standard `type` field |
| 17 | Style | `LoggingFilter.java:36` | `System.out.println` duplicates the logger call |
| 18 | Style | `CartService.java:116` | `toResponse` / `toResponseWithPrices` split duplicates logic |
| 19 | Medium Test | `OrdersResourceTest.java` | No `@BeforeEach` cleanup — tests share and pollute DB state |
| 20 | Smell Test | `PriceCacheServiceTest.java` | `getPrice` caching path is not tested |
