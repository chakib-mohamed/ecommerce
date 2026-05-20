# Code Review: `products-service`

> Reviewed: 2026-05-20  
> Includes `products-api` shared module.

## Bugs

### 1. `updateProduct` silently inserts instead of updating when product not found
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:50-66`  
**Severity:** Bug

When `existing == null` (UUID not matched), the code falls through without setting `product.id`. The subsequent `em.merge(product)` then sees an entity with a `null` id and attempts an insert — creating a duplicate row instead of returning an error. The resource always returns 200.

**Fix:** throw a `ProductNotFoundException` (404) when `existing == null` and let the `GlobalExceptionHandler` handle it.

---

### 2. `updateProduct` with `BETWEEN` operator produces invalid JPQL
**File:** `products-api/src/main/java/the/chak/ecommerce/products/boundary/dto/Criteria.java:22`, `ProductService.java:90-99`  
**Severity:** Bug

`Criteria.Operator.BETWEEN` maps to `" BETWEEN "`. The `findByCriteria` query builder produces `:key` as the only placeholder, yielding `field BETWEEN :field` — invalid JPQL (BETWEEN needs two bounds: `BETWEEN :from AND :to`). Any client sending `BETWEEN` will get a runtime JPQL error. Either implement proper BETWEEN binding or remove the enum value.

---

### 3. `@OneToMany(mappedBy = "parentId")` on `Category.subCategories` is incorrect
**File:** `src/main/java/the/chak/ecommerce/products/entity/Category.java:24`  
**Severity:** Bug

`mappedBy` must reference the field name of the relationship in the owning entity — not a column name. The owning side field `parentId` is a `Long`, not a `@ManyToOne Category` reference, so JPA/Hibernate cannot use it as a proper relationship. The self-referential mapping is broken. Fix: replace the `Long parentId` field with a proper `@ManyToOne Category parent` relationship, and update the schema accordingly.

---

### 4. `PromotionService.deletePromotion` loads an unused entity
**File:** `src/main/java/the/chak/ecommerce/products/control/PromotionService.java:36-43`  
**Severity:** Bug

```java
Promotion promotion = Promotion.findById(promotionID); // never used
Promotion.deleteById(promotionID);
```

`promotion` was intended for the now-commented-out event logic. It is unused today, adding a pointless DB round-trip. Remove the `findById` call. Also, `deleteById` returns `false` silently when the ID doesn't exist — the caller gets a 200 instead of 404.

---

### 5. `PriceChangedConsumer` silently discards events for unknown products
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:83-88`  
**Severity:** Bug

When `updatePrice` receives a `productId` that matches no product, it does nothing — no log, no exception, no dead-letter. A price change event is consumed and lost. Add at minimum a `log.warn(...)` and consider sending the message to a dead-letter topic.

---

## Security

### 6. JPQL injection via `findByCriteria`
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:91-93`, `CategoryService.java:44-47`  
**Severity:** Critical

The `key` from the client's HTTP request body is directly string-concatenated into the JPQL query:

```java
query.append(" and ").append(key)   // key comes from Map<String, Criteria> in the HTTP body
     .append(criteria.getOperator().getValue())
     .append(" :").append(key);
```

An attacker can inject arbitrary JPQL by controlling the map key. Even though JPQL injection is less dangerous than SQL injection, it can still bypass predicates, cause data disclosure, or throw internal exceptions.

**Fix:** validate each `key` against an explicit whitelist of allowed field names before building the query.

---

### 7. `MinioService` hardcodes `Content-Type: image/jpeg` for all uploads
**File:** `src/main/java/the/chak/ecommerce/products/control/MinioService.java:66`  
**Severity:** Low

```java
metadata.setContentType("image/jpeg"); // Basic assumption
```

PNG, GIF, and WebP images are uploaded with the wrong MIME type. The `ImageValidator` correctly validates the format, but the stored metadata is always `image/jpeg`. Derive the content type from the magic bytes validated by `ImageValidator`.

---

## Architecture

### 8. Banned `quarkus-rest` (reactive REST) in use
**File:** `pom.xml:43`  
**Severity:** High

`quarkus-rest` is the reactive REST extension, explicitly banned by `backend/CLAUDE.md`. Replace with `quarkus-resteasy` (blocking JAX-RS) and `quarkus-resteasy-jsonb`.

---

### 9. `MinioService` bypasses the Quarkus S3 extension
**File:** `src/main/java/the/chak/ecommerce/products/control/MinioService.java`  
**Severity:** Medium

The service reads `quarkus.s3.*` property names via `@ConfigProperty` but instantiates a raw AWS SDK v1 `AmazonS3Client` itself — the Quarkus S3 extension (`quarkus-amazon-s3`) is not in `pom.xml` and plays no role. This means: (a) the `quarkus.s3.*` naming is misleading, (b) AWS SDK v1 is in maintenance mode, and (c) health checks, metrics, and injection provided by the Quarkus extension are absent. Either adopt `quarkus-amazon-s3` or rename the config properties to avoid the `quarkus.s3.*` namespace.

---

### 10. `ExceptionHandler` diverges from the project's exception-handling convention
**File:** `src/main/java/the/chak/ecommerce/products/boundary/ExceptionHandler.java`  
**Severity:** Medium

The project convention (`backend/CLAUDE.md`) requires a `GlobalExceptionHandler` using `@ServerExceptionMapper` and the `FunctionalException` hierarchy with `ErrorResponse { type, message }`. This service instead implements JAX-RS `ExceptionMapper<Exception>` with custom `ErrorDto`/`ErrorCode` types. The service also has no `FunctionalException` subclasses — domain errors are thrown as `IllegalArgumentException`, which leaks implementation details in the error message. Align with the project convention.

---

### 11. `ProductService` injects `ProductMapper` (boundary leaking into service)
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:25-26`  
**Severity:** Smell

`ProductMapper` is a boundary/presentation concern (MapStruct mapper for DTOs). The service layer should deal only in entities. The mapping needed to fire `ProductUpdatedEvent` (which wraps a DTO) should happen in the resource class or the event publisher, not in the service.

---

### 12. `product.uuid` lacks a `UNIQUE` constraint in the schema
**File:** `src/main/resources/db/changelog/changes/001-init-schema.sql:32`  
**Severity:** Smell

`CREATE INDEX idx_product_uuid ON product(uuid)` creates an index but no `UNIQUE` constraint. The application relies on UUID uniqueness for all lookups (`find("uuid", uuid).firstResult()`), but two products could theoretically share a UUID at the database level (e.g., bug in `@PrePersist`). Add `UNIQUE` to the constraint.

---

### 13. `import.sql.bu` backup file committed to version control
**File:** `src/main/resources/import.sql.bu`  
**Severity:** Smell

A `.bu` (backup) file is tracked in git. Remove it and add `*.bu` to `.gitignore`.

---

## Readability / Code Quality

### 14. `CategoryService.findByCriteria` uses `Integer.MAX_VALUE` as page size
**File:** `src/main/java/the/chak/ecommerce/products/control/CategoryService.java:58`  
**Severity:** Medium

```java
public List<Category> findByCriteria(Map<String, Criteria> params) {
    return findByCriteria(params, 0, Integer.MAX_VALUE);
}
```

Using `Integer.MAX_VALUE` as a page size when the caller just wants "all results" risks OOM if the table grows. Replace with `Category.find(...).list()` (no page limit) or `Category.listAll()` for the empty-params case.

---

### 15. `PromotionService.savePromotion` and `deletePromotion` contain large commented-out blocks
**File:** `src/main/java/the/chak/ecommerce/products/control/PromotionService.java:27-30, 40-41`  
**Severity:** Style

Dead commented-out code should be removed; if the feature is planned, open a ticket instead.

---

### 16. `ProductDto` has redundant `@JsonbTypeAdapter` on field, getter, and setter
**File:** `products-api/src/main/java/the/chak/ecommerce/products/boundary/dto/ProductDto.java:14, 17, 22`  
**Severity:** Style

The `@JsonbTypeAdapter` annotation on the field is sufficient. Repeating it on the getter and setter is redundant noise and can cause unexpected double-adapting depending on the JSON-B implementation.

---

### 17. `getProducts` uses a 0-check instead of `@DefaultValue`
**File:** `src/main/java/the/chak/ecommerce/products/boundary/ProductsResource.java:38-39`  
**Severity:** Style

```java
int effectivePageIndex = (pageIndex == 0) ? defaultPageIndex : pageIndex;
```

`0` is a valid page index (first page). The check conflates "not provided" with "provided as 0", making page 0 unreachable if the config default is also 0. Use `@DefaultValue` on the query param instead.

---

### 18. `Response.ok().status(201)` pattern in resource classes
**File:** `ProductsResource.java:70`, `CategoriesResource.java:47`, `PromotionsResource.java:44`  
**Severity:** Style

Same anti-pattern as authenticate-service: `.ok()` sets 200 then `.status(201)` overrides it. Use `Response.status(Response.Status.CREATED).entity(...)` uniformly.

---

## Tests

### 19. `CategoriesResourceTest` has two tests with `@Order(4)`
**File:** `src/test/java/the/chak/ecommerce/products/boundary/CategoriesResourceTest.java:73, 104`  
**Severity:** Bug

`testSearchCategories` and `testDeleteCategory` both carry `@Order(4)`. JUnit's `@TestMethodOrder(OrderAnnotation)` leaves ties undefined, so these two tests may run in any order. `testDeleteCategory` creates a category that `testSearchCategories` doesn't know about, but both share the same DB state, risking interference. Fix: assign unique order values.

---

### 20. `ProductsResourceTest` test 4 asserts 400 on invalid image but `MinioService` has no image validation
**File:** `src/test/java/the/chak/ecommerce/products/boundary/ProductsResourceTest.java:134-142`  
**Severity:** Smell

The test expects `statusCode(400)` when an invalid base64 image is sent. This works because `@ValidImage` on `ProductDto.image` catches non-image bytes before they reach `MinioService`. However, `MinioService.uploadImage` has no own validation — if the validator is ever bypassed (e.g., internal call), any bytes are uploaded unconditionally.

---

## Performance

### 21. N+1 query risk on `getProducts` and `getProduct`
**File:** `src/main/java/the/chak/ecommerce/products/boundary/ProductsResource.java:41-43`  
**Severity:** Medium

`Product.findAll().page(...).list()` fetches products without eagerly joining `promotions` and `categories`. When the mapper accesses `p.getPromotions()` or `p.getCategories()`, Hibernate fires a separate SQL query per product (classic N+1). `InitService` already demonstrates the fix with `"from Product p left join fetch p.promotions"`. Add `JOIN FETCH` to the listing and single-product queries.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug | `ProductService.java:50` | Update silently inserts instead of returning 404 |
| 2 | Bug | `Criteria.java:22` / `ProductService.java:92` | BETWEEN operator produces invalid JPQL |
| 3 | Bug | `Category.java:24` | `@OneToMany(mappedBy)` on a `Long` field — broken JPA mapping |
| 4 | Bug | `PromotionService.java:36` | Unused `findById` + silent no-op on missing ID |
| 5 | Bug | `ProductService.java:83` | Price-changed event silently dropped for unknown products |
| 6 | Critical Security | `ProductService.java:91` / `CategoryService.java:44` | JPQL injection via unsanitised map keys |
| 7 | Low Security | `MinioService.java:66` | Content-Type hardcoded as `image/jpeg` for all uploads |
| 8 | High Arch | `pom.xml:43` | Banned `quarkus-rest` (reactive) in use |
| 9 | Medium Arch | `MinioService.java` | AWS SDK v1 bypasses Quarkus S3 extension |
| 10 | Medium Arch | `ExceptionHandler.java` | Diverges from project `GlobalExceptionHandler` convention |
| 11 | Smell | `ProductService.java:25` | `ProductMapper` injected into service layer |
| 12 | Smell | `001-init-schema.sql:32` | `product.uuid` missing `UNIQUE` constraint |
| 13 | Smell | `import.sql.bu` | Backup file committed to version control |
| 14 | Medium | `CategoryService.java:58` | `Integer.MAX_VALUE` as page size risks OOM |
| 15 | Style | `PromotionService.java:27` | Large commented-out code blocks |
| 16 | Style | `ProductDto.java:14` | Redundant `@JsonbTypeAdapter` on field, getter, and setter |
| 17 | Style | `ProductsResource.java:38` | Zero-check for pagination conflates "default" with "first page" |
| 18 | Style | Multiple resource classes | `Response.ok().status(201)` anti-pattern |
| 19 | Bug (Test) | `CategoriesResourceTest.java:73,104` | Two tests with `@Order(4)` — undefined execution order |
| 20 | Smell (Test) | `ProductsResourceTest.java:134` | Invalid-image 400 depends entirely on DTO validation layer |
| 21 | Medium Perf | `ProductsResource.java:41` | N+1 query on `getProducts` / `getProduct` |
