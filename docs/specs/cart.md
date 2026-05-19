# Spec: Cart (module inside orders-service)

## Objective

Add cart functionality as a module inside the existing `orders-service`. A cart is a draft order — the lifecycle is `cart → checkout → order`, all owned by the same domain. Keeping them co-located eliminates the distributed transaction problem at checkout and avoids the operational cost of a new service.

**User stories:**
- As a user, I can add a product (by ID) and quantity to my cart
- As a user, I can view my cart with live prices fetched from `price-service`
- As a user, I can update the quantity of an item in my cart
- As a user, I can remove a single item from my cart
- As a user, I can clear my entire cart
- As a user, checking out atomically creates an order and clears my cart in a single MongoDB transaction
- As a user, my cart persists across sessions and expires after 30 days of inactivity

**Out of scope:**
- Guest (unauthenticated) carts — rejected at the gateway
- Stock validation on add — deferred to checkout
- Multi-cart or wishlist support

---

## Tech Stack

Same as `orders-service` — no new dependencies required.

| Concern | Choice |
|---|---|
| Framework | Quarkus 3.17.6 |
| Language | Java 21 |
| Database | MongoDB already used by orders-service (Panache active-record) |
| Auth | JWT subject claim (`@Claim(Claims.sub)`) — gateway validates, service extracts user ID |
| HTTP client | MicroProfile REST Client → `price-service` at port 8085 (new dependency on orders-service) |
| Price cache | Redis (`quarkus-redis-cache` extension) — TTL configurable via `cart.price-cache.ttl-minutes`, default 15 |
| Port | 8084 (unchanged — cart lives inside orders-service) |

---

## Commands

```bash
# From backend/ directory
./mvnw clean package -DskipTests -pl orders-service   # build
./mvnw quarkus:dev -pl orders-service                  # dev mode (hot reload)
./mvnw test -pl orders-service                         # run tests (Testcontainers — Docker required)
```

---

## Project Structure

Cart files are added inside the existing `orders-service` package tree. No new top-level module.

```
backend/orders-service/src/main/java/chakmed/ecommerce/orders/
├── boundary/
│   ├── OrderResource.java             # existing
│   ├── CartResource.java              # NEW — JAX-RS cart endpoints
│   ├── AddItemRequest.java            # NEW — { productId, quantity }
│   ├── UpdateItemRequest.java         # NEW — { quantity }
│   ├── CartResponse.java              # NEW — { userId, items[], updatedAt }
│   ├── CartItemResponse.java          # NEW — { productId, quantity, unitPrice, totalPrice }
│   ├── PriceClient.java               # NEW — MicroProfile REST Client → price-service
│   └── PriceCacheService.java         # NEW — Redis cache wrapper (TTL-aware, configurable)
├── control/
│   ├── OrderService.java              # existing
│   └── CartService.java               # NEW — add/remove/checkout logic
└── entity/
    ├── Order.java                     # existing
    ├── Cart.java                      # NEW — MongoDB document (userId, items, updatedAt)
    └── CartItem.java                  # NEW — embedded { productId, quantity }

backend/orders-service/src/test/java/chakmed/ecommerce/orders/
├── CartResourceTest.java              # NEW
└── CartServiceTest.java               # NEW
```

---

## Code Style

Follow the boundary/control/entity layering already established in `orders-service`. Active-record pattern — queries live on the entity class.

```java
// Entity: Cart.java
@MongoEntity(collection = "carts")
public class Cart extends PanacheMongoEntity {
    public String userId;
    public List<CartItem> items = new ArrayList<>();
    public Instant updatedAt;

    public static Optional<Cart> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }
}

// Boundary: CartResource.java
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource {
    @Inject CartService cartService;
    @Inject @Claim(Claims.sub) String userId;

    @GET
    public CartResponse getCart() { ... }
}

// Control: CartService.java — orchestrates Cart entity + PriceCacheService, no Panache calls directly

// PriceCacheService.java — cache-aside pattern
@ApplicationScoped
public class PriceCacheService {
    @Inject PriceClient priceClient;
    @Inject RedisDataSource redis;
    @ConfigProperty(name = "cart.price-cache.ttl-minutes", defaultValue = "15")
    int ttlMinutes;

    public BigDecimal getPrice(String productId) {
        // 1. check Redis → 2. on miss, call priceClient → 3. store with TTL → 4. return
    }
}
```

Cache key pattern: `price:{productId}`. TTL configured in `application.properties`:

```properties
cart.price-cache.ttl-minutes=15
```

Naming: `camelCase` fields, `PascalCase` classes. No `@JsonProperty` renaming unless matching an existing API contract.

---

## Testing Strategy

**TDD is mandatory — tests are written before implementation code.**

Order of work for every task:
1. Write a failing test that captures the acceptance criterion
2. Run it — confirm it fails for the right reason
3. Write the minimum implementation to make it pass
4. Refactor if needed, keeping tests green

- **Framework**: JUnit 5 + REST Assured + Mockito + Testcontainers (already configured in orders-service)
- **MongoDB**: Testcontainers `MongoDBContainer` — reuse existing test setup in orders-service
- **PriceClient**: Mocked with `@InjectMock` — no real calls to price-service in tests
- **Redis**: Testcontainers `RedisContainer` for `PriceCacheService` integration tests; mock Redis for unit tests
- **Test location**: `src/test/java/chakmed/ecommerce/orders/`
- **Coverage target**: all happy paths + key error cases (item not in cart, empty cart checkout, price-service unavailable)

```bash
./mvnw test -pl orders-service   # Docker must be running for Testcontainers
```

---

## Boundaries

**Always:**
- Write the OpenAPI spec (`openapi.yaml`) before writing any implementation code
- Write a failing test before writing the implementation it covers (TDD)
- Extract user identity from `@Claim(Claims.sub)` — never from a request body or query param
- Fetch price live from `price-service` at response time — never persist price in the cart document
- Update `cart.updatedAt` on every mutation (drives the 30-day TTL index)
- Run `./mvnw test -pl orders-service` before committing
- Follow boundary/control/entity package structure

**Ask first:**
- Changing the `Cart` or `CartItem` MongoDB document schema
- Adding any service-to-service dependency beyond `price-service`
- Changing the cache TTL default or cache key scheme
- Adding a Kafka topic for cart events (e.g., cart-abandoned)

**Never:**
- Store price in the `CartItem` document — always serve from cache or live `price-service`
- Implement stock validation on add — deferred to checkout
- Bypass the JWT claim for user identification
- Commit `application-dev.properties` secrets

---

## Contract Strategy

### Service-to-Service Contracts (orders-service ↔ price-service)

**Chosen approach: shared DTO module** (`orders-api` or a new `price-api`).

Both `orders-service` (consumer via `PriceClient`) and `price-service` (provider) depend on the same DTO classes. Because `PriceClient` is a MicroProfile REST Client using the shared DTO for both serialization and deserialization, JSON field names are guaranteed to match by construction — no runtime mismatch is possible.

**Known tradeoffs accepted:**

| Flaw | Impact | Mitigation |
|---|---|---|
| Lockstep releases | Breaking DTO change requires rebuilding all consumers simultaneously | Acceptable — all services are deployed together in this project |
| Provider evolution blocked | `price-service` can't rename/remove fields without coordinating consumers | Use additive changes only (new optional fields); never remove fields without a deprecation cycle |
| No HTTP-layer verification | Status codes, paths, and query params are not enforced by the shared module | Covered by the OpenAPI spec + REST Assured tests in each service |

**What this approach does NOT replace:** the `openapi.yaml` still defines the full HTTP contract (status codes, paths, error responses). The shared DTO module covers body shape only.

---

## OpenAPI Contract

**The OpenAPI spec is written before any implementation code.** It is the source of truth for the API — implementation must conform to it, not the other way around.

File location: `backend/orders-service/src/main/resources/META-INF/openapi.yaml`

Quarkus serves it at `http://localhost:8084/q/openapi` in dev mode. Add to `application.properties`:
```properties
quarkus.smallrye-openapi.store-schema-directory=src/main/resources/META-INF
mp.openapi.extensions.smallrye.operationIdStrategy=METHOD
```

The spec must fully define all request bodies, response schemas, status codes, and error responses before `CartResource.java` is created. The table below is the contract summary — the `openapi.yaml` is the authoritative definition.

| Method | Path | Request body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/cart` | — | `200 CartResponse` | `404` no cart |
| `POST` | `/api/cart/items` | `AddItemRequest` | `200 CartResponse` | `400` invalid quantity |
| `PUT` | `/api/cart/items/{productId}` | `UpdateItemRequest` | `200 CartResponse` | `404` item not in cart |
| `DELETE` | `/api/cart/items/{productId}` | — | `204` | `404` item not in cart |
| `DELETE` | `/api/cart` | — | `204` | `404` no cart |
| `POST` | `/api/cart/checkout` | — | `201 OrderResponse` | `400` empty cart, `503` price-service down |

All endpoints require a valid JWT (enforced at the gateway). `401` is returned by the gateway, not the service.

---

## Success Criteria

- [ ] `openapi.yaml` exists and fully defines all request/response schemas before any implementation file is created
- [ ] All tests are written and failing before their corresponding implementation exists
- [ ] `GET /api/cart` returns items with `unitPrice` and `totalPrice` — served from Redis cache when available, falling back to `price-service`
- [ ] Cache TTL is respected: price is re-fetched from `price-service` after `cart.price-cache.ttl-minutes` minutes
- [ ] Cache TTL is configurable via `application.properties` without code changes
- [ ] `POST /api/cart/items` creates a cart if none exists; increments quantity if item already present
- [ ] `PUT /api/cart/items/{productId}` returns `404` if the item is not in the cart
- [ ] `DELETE /api/cart` returns `204` and the cart document is removed from MongoDB
- [ ] `POST /api/cart/checkout` persists an order and clears the cart atomically; cart is unchanged if order creation fails
- [ ] Cart document has a MongoDB TTL index of 30 days on the `updatedAt` field
- [ ] `PriceClient` unavailability returns `503` with a clear error message — cart is not corrupted
- [ ] All tests pass with `./mvnw test -pl orders-service`
- [ ] Gateway route `/api/cart/**` → `http://orders-service:8084` added to `ecommerce-api-gateway`
- [ ] No new Docker container, no new port, no change to `docker-compose.yml` service list

---

## Open Questions

None — all decisions resolved before spec was written.
