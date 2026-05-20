# Code Review: `featured-products-service`

> Reviewed: 2026-05-20

## Bugs

### 1. NPE when consuming a product with null categories or promotions
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:34-36`  
**Severity:** Bug

```java
productMongoEntity.setCategories(
    product.getCategories().stream()...);   // NPE if null
productMongoEntity.setPromotions(
    product.getPromotions().stream()...);   // NPE if null
```

`ProductDto.getCategories()` and `getPromotions()` both return `null` when not set in the Kafka payload (e.g., a product with no categories). This crashes the Kafka consumer with an unhandled `NullPointerException`, which Kafka will requeue indefinitely (or send to DLQ, if configured). Guard with `Optional.ofNullable(...).orElse(List.of())` or use `List.of()` as the default in the DTO.

---

### 2. Tests rely on banned Dev Services via empty connection strings
**File:** `src/main/resources/application.properties:10,23-24`  
**Severity:** Bug

```properties
%test.quarkus.mongodb.connection-string=
%test.mp.messaging.connector.smallrye-kafka.bootstrap.servers=
```

`backend/CLAUDE.md` explicitly bans this pattern: "Do not rely on empty connection strings to trigger Dev Services." There are no `QuarkusTestResourceLifecycleManager` implementations in this service — no MongoDB container, no Kafka container. The tests either silently rely on Dev Services or fail when Docker is unavailable. Add proper `MongoDbTestResource` and `KafkaTestResource` classes and annotate `KafkaEventConsumerTest` with `@QuarkusTestResource` for each.

---

### 3. `productID` field has no unique index in MongoDB
**File:** `src/main/java/the/chak/ecommerce/products/entity/ProductMongoEntity.java:23-25`  
**Severity:** Bug

`findByUuid` calls `find("productID", productID).firstResult()`, and `onProductDeleted` filters by `productID`. Neither MongoDB nor Panache enforces uniqueness on this field. If a duplicate is written (e.g., due to a Kafka message being redelivered mid-persist), `findByUuid` silently returns the first match and the second document becomes an orphan. Add a unique index on `productID` via the `@MongoEntity` annotation or an explicit MongoDB command at startup.

---

## Architecture

### 4. Banned `quarkus-rest` (reactive REST) in use
**File:** `pom.xml:42`  
**Severity:** High

`quarkus-rest` is the reactive REST extension, explicitly banned by `backend/CLAUDE.md`. Replace with `quarkus-resteasy` and `quarkus-resteasy-jsonb`.

---

### 5. Unused `quarkus-jdbc-postgresql` dependency
**File:** `pom.xml:71-74`  
**Severity:** Medium

This service is MongoDB-only; it has no relational datasource. The PostgreSQL JDBC driver adds to the classpath and image size for no reason. Remove it.

---

### 6. `defaultPageIndex` and `defaultPageSize` config injections are dead code
**File:** `src/main/java/the/chak/ecommerce/products/boundary/FeaturedProductsResource.java:22-26`  
**Severity:** Medium

```java
@ConfigProperty(name = "products.default.page.index", defaultValue = "0")
int defaultPageIndex;

@ConfigProperty(name = "products.default.page.size", defaultValue = "10")
int defaultPageSize;
```

These fields are never read. The method uses `@DefaultValue("0")` and `@DefaultValue("10")` on the `@QueryParam` annotations directly. Either remove the `@ConfigProperty` fields and keep `@DefaultValue`, or wire the config values into the defaults properly. As-is, changing the config properties has no effect.

---

### 7. Missing `GlobalExceptionHandler`
**Severity:** Medium

No exception handler exists. Runtime exceptions from `mapProductToProductMongoEntity` (NPE, `ClassCastException`) or from the REST layer propagate as unstructured 500 responses. Add a `GlobalExceptionHandler` with `@ServerExceptionMapper` per the project convention in `backend/CLAUDE.md`.

---

### 8. Promotions and categories stored as raw `List<Document>`
**File:** `src/main/java/the/chak/ecommerce/products/entity/ProductMongoEntity.java:20-21`  
**Severity:** Smell

Using `List<Document>` (BSON raw document) for embedded data is untyped. When the schema of a promotion or category changes, deserialization silently returns `null` for missing fields rather than failing fast. Define dedicated embedded classes (e.g., `EmbeddedPromotion`, `EmbeddedCategory`) and let Panache deserialize into them.

---

### 9. `mapProductToProductMongoEntity` is `public` without reason
**File:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:23`  
**Severity:** Smell

This is a pure service-internal mapping helper. Making it `public` widens the API surface for no benefit and couples callers to an implementation detail. Make it `private`.

---

### 10. Commented-out Eureka dependency in `pom.xml`
**File:** `pom.xml:94-98`  
**Severity:** Smell

Dead commented-out dependency. Remove it; version control preserves the history.

---

## Readability / Code Quality

### 11. `System.out.println` debug statements in production code
**Files:** `src/main/java/the/chak/ecommerce/products/control/ProductService.java:17,52`  
`src/main/java/the/chak/ecommerce/products/boundary/CustomJsonbConfigCustomizer.java:13`  
**Severity:** Medium

Three `System.out.println("DEBUG: ...")` calls were left in production code. Replace with `log.debug(...)` (inject a `Logger` with `@Slf4j` or `@Inject Logger`).

---

### 12. `toCategoryDto` silently drops the `id` field
**File:** `src/main/java/the/chak/ecommerce/products/boundary/mapper/ProductMapper.java:29-31`  
**Severity:** Medium

```java
default CategoryDto toCategoryDto(Document document) {
    CategoryDto categoryDto = new CategoryDto();
    categoryDto.setLabel(document.getString("label"));  // id is not mapped
    return categoryDto;
}
```

The `id` is stored in MongoDB (see `categoryDtoToDocument` in `ProductService`), but never restored during mapping. Featured-product API responses return categories without IDs, creating a schema inconsistency with the products-service API.

---

### 13. `ProductMapper` uses `componentModel = "jakarta"` while other services use `"cdi"`
**File:** `src/main/java/the/chak/ecommerce/products/boundary/mapper/ProductMapper.java:10`  
**Severity:** Style

`authenticate-service` uses `componentModel = "cdi"`. Both work in Quarkus (they are aliases), but the codebase should be consistent. Standardise on one.

---

### 14. Verbose and redundant stream pipeline in `getFeaturedProducts`
**File:** `src/main/java/the/chak/ecommerce/products/boundary/FeaturedProductsResource.java:34-39`  
**Severity:** Style

```java
List<ProductMongoEntityDto> dtos = ProductMongoEntity.findAll().page(pageIndex, pageSize)
    .stream().map(ProductMongoEntity.class::cast).map(p -> {
        ProductMongoEntityDto dto = productMapper.toDto(p);
        return dto;
    }).collect(Collectors.toList());
return dtos;
```

Simplify to:

```java
return ProductMongoEntity.<ProductMongoEntity>findAll()
    .page(pageIndex, pageSize).list()
    .stream().map(productMapper::toDto).toList();
```

---

## Performance

### 15. No index on `productID` — every lookup is a full collection scan
**File:** `src/main/java/the/chak/ecommerce/products/entity/ProductMongoEntity.java:23-25`  
**Severity:** Medium

`find("productID", productID)` runs on every Kafka event consumed and on every `persistOrUpdate`. With no index, this is an O(N) scan. Define a unique index on the `productID` field via `@MongoEntity(indexes = @MongoIndex(fields = { @MongoIndexField(value = "productID") }, unique = true))` or equivalent.

---

## Tests

### 16. `KafkaEventConsumerTest` has no `@QuarkusTestResource` annotations
**File:** `src/test/java/the/chak/ecommerce/products/control/KafkaEventConsumerTest.java:24`  
**Severity:** Bug (see also item 2)

The test class is `@QuarkusTest` with no `@QuarkusTestResource`. It injects `@Channel` emitters and calls `ProductMongoEntity.deleteAll()` — both require live Kafka and MongoDB. Without explicit test resources, the test passes only because Dev Services are triggered by empty connection strings in `application.properties`, which is the banned pattern. Add `MongoDbTestResource` and `KafkaTestResource` and annotate the class accordingly.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug | `ProductService.java:34` | NPE when categories or promotions are null in Kafka event |
| 2 | Bug | `application.properties:10,23` | Tests rely on banned Dev Services via empty connection strings |
| 3 | Bug | `ProductMongoEntity.java:23` | No unique index on `productID` — duplicates possible |
| 4 | High Arch | `pom.xml:42` | Banned `quarkus-rest` (reactive) in use |
| 5 | Medium Arch | `pom.xml:71` | Unused `quarkus-jdbc-postgresql` dependency |
| 6 | Medium Arch | `FeaturedProductsResource.java:22` | `@ConfigProperty` page config fields are dead code |
| 7 | Medium Arch | Service-wide | Missing `GlobalExceptionHandler` |
| 8 | Smell | `ProductMongoEntity.java:20` | Raw `List<Document>` for embedded data — untyped and fragile |
| 9 | Smell | `ProductService.java:23` | `mapProductToProductMongoEntity` is unnecessarily `public` |
| 10 | Smell | `pom.xml:94` | Commented-out Eureka dependency |
| 11 | Medium | `ProductService.java:17,52` / `CustomJsonbConfigCustomizer.java:13` | `System.out.println` debug statements in production code |
| 12 | Medium | `ProductMapper.java:29` | `toCategoryDto` drops the `id` field — API inconsistency |
| 13 | Style | `ProductMapper.java:10` | `componentModel = "jakarta"` vs `"cdi"` inconsistency |
| 14 | Style | `FeaturedProductsResource.java:34` | Verbose stream pipeline — simplify with `.list()` and method reference |
| 15 | Medium Perf | `ProductMongoEntity.java:23` | No MongoDB index on `productID` — full collection scan on every lookup |
| 16 | Bug (Test) | `KafkaEventConsumerTest.java:24` | No `@QuarkusTestResource` — relies on banned Dev Services |
