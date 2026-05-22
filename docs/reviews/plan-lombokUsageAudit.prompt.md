# Lombok Usage Audit & Anti-Pattern Remediation

The codebase uses Lombok across all 7 backend modules. I found several anti-patterns: `@Data` on JPA/Panache entities (broken equals/hashCode), `@Data` on CDI config beans (exposes setters for immutable config), mixed access modifiers with `@Getter`/`@Setter`, and redundant manual method overrides alongside Lombok annotations.

## Findings

### 1. `@Data` on persistence entities — HIGH severity
- [Price.java](backend/price-service/src/main/java/the/chak/ecommerce/pricing/entity/Price.java): `@Data` on a `PanacheMongoEntity` generates `equals`/`hashCode` on mutable fields, breaks identity semantics. The `@EqualsAndHashCode(callSuper = false)` suppresses the parent but still bases equality on `productId`+`price` instead of the DB id.
- [Cart.java](backend/orders-service/src/main/java/the/chak/ecommerce/orders/entity/Cart.java): `@Data` + `@EqualsAndHashCode(callSuper = true)` — includes `PanacheMongoEntity.id` which is null before persist, causing hash instability in collections.

### 2. `@Data` on CDI/Spring config beans — MEDIUM severity
- [JwtConfig.java (auth-service)](backend/authenticate-service/src/main/java/the/chak/ecommerce/authentication/control/JwtConfig.java): `@Data` generates public setters on a `@ConfigProperty`-injected, `@ApplicationScoped` bean — anyone can mutate the private key at runtime.
- [JwtConfig.java (gateway)](backend/ecommerce-api-gateway/src/main/java/the/chak/ecommerce/apigateway/JwtConfig.java): Same issue with Spring `@Value` fields.

### 3. Mixed visibility with `@Getter`/`@Setter` on classes with public fields — LOW severity
- [PromotionDto.java](backend/products-api/src/main/java/the/chak/ecommerce/products/boundary/dto/PromotionDto.java): `@Getter`+`@Setter` at class level but fields are `public` — the annotations are redundant/confusing.
- [User.java](backend/authenticate-service/src/main/java/the/chak/ecommerce/authentication/entity/User.java): `@Getter`+`@Setter` at class level but fields are `public` — same redundancy.
- Panache MongoDB entities (`Price`, `Cart`) have `public` fields (Panache convention) **and** `@Data` which generates getters/setters — double access paths.

### 4. Redundant manual methods alongside Lombok
- [ProductDto.java](backend/products-api/src/main/java/the/chak/ecommerce/products/boundary/dto/ProductDto.java): `@Data` is present but `getImage()`/`setImage()` are manually written — the manual methods shadow Lombok's, making `@Data` partially dead code (confusing for readers).
- [Product.java](backend/products-service/src/main/java/the/chak/ecommerce/products/entity/Product.java) & [Category.java](backend/products-service/src/main/java/the/chak/ecommerce/products/entity/Category.java): `@Getter` at class level + manual `getPromotions()`/`getCategories()`/`getSubCategories()` — valid (lazy-init logic) but should be documented or the class-level annotation excluded for those fields.

### 5. `@Data` on DTOs that should be immutable — MEDIUM severity
- All DTOs (e.g., `PriceResponse`, `AuthenticateResponse`, `ErrorResponse`) use `@Data` which generates setters. Response DTOs should be immutable — `@Value` (Lombok) or Java records would be safer.

## Recommended Steps

1. **Entities**: Replace `@Data` with `@Getter`/`@Setter` on entities; remove or fix `@EqualsAndHashCode` (use id-based equality or don't override at all for Panache entities).
2. **Config beans**: Replace `@Data` with `@Getter` only on `JwtConfig` classes to prevent runtime mutation.
3. **Response DTOs**: Convert to Java 17 records or use `@Value` (immutable) instead of `@Data` where no deserialization is needed; keep `@Data` only on request DTOs that need setters for JSON binding.
4. **Public fields + `@Getter`/`@Setter`**: Pick one convention — either use `public` fields (Panache Mongo style) without accessor annotations, or use `private` fields with Lombok accessors.
5. **Manual overrides**: Add `@Getter(AccessLevel.NONE)` on `image` field in `ProductDto`, and on collection fields in `Product`/`Category` to make the intent explicit and prevent dead-code confusion.

## Further Considerations

1. **Java records vs Lombok `@Value`** — Since the project targets Java 17+ (Quarkus 3.x), records are idiomatic for DTOs. However, JSON-B deserialization may require `@NoArgsConstructor` which records don't support — test with your serialization framework first.
2. **Panache field-access convention** — Panache MongoDB uses public fields by design and rewrites field access to getter/setter calls at build time. Adding Lombok `@Getter`/`@Setter` on top is redundant and can confuse the bytecode enhancement — recommend removing Lombok entirely from Panache Mongo entities.
3. **Scope of change** — Would you prefer a single sweep across all services, or service-by-service PRs to reduce risk?

