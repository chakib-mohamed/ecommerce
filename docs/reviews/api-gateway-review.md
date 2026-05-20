# Code Review: `ecommerce-api-gateway`

> Reviewed: 2026-05-20

## Bugs

### 1. Thread-local `SecurityContextHolder` used in reactive context
**File:** `src/main/java/the/chak/ecommerce/apigateway/control/AuthenticationManager.java:27,30`  
**Severity:** Bug / Security

`SecurityContextHolder` is thread-local and must not be used inside a WebFlux reactive pipeline. Netty's event-loop threads are shared across requests, so calling `SecurityContextHolder.getContext().setAuthentication(auth)` or `clearContext()` inside a reactive chain can silently corrupt or clear another request's security context.

The security context is already propagated by `securityContextRepository` — the two `SecurityContextHolder` calls should simply be removed.

---

### 2. Security permit rule references non-existent route path
**File:** `src/main/java/the/chak/ecommerce/apigateway/SecurityConfig.java:71`  
**Severity:** Bug

`.pathMatchers(HttpMethod.GET, "/api/featured-products/**").permitAll()` matches no route. The actual route in `application.yml` is `/api/products/featured`. The permit rule is dead, and the real featured-products endpoint falls through to the `.anyExchange().authenticated()` catch-all — making it require a token when it shouldn't.

**Fix:** Change to `.pathMatchers(HttpMethod.GET, "/api/products/featured").permitAll()`.

---

### 3. Prefix stripped with `replace()` instead of `substring()`
**File:** `src/main/java/the/chak/ecommerce/apigateway/control/AuthHeaderTokenResolver.java:20`  
**Severity:** Bug

```java
t.replace(jwtConfig.getPrefix(), "")
```
`String.replace()` replaces every occurrence of the prefix in the token string, not just the leading one. Should be:
```java
t.substring(jwtConfig.getPrefix().length())
```

---

## Security

### 4. Full JWT token logged at INFO level
**File:** `src/main/java/the/chak/ecommerce/apigateway/boundary/ApiGatewayController.java:29`  
**Severity:** High

```java
log.info("Revoking token: {}", revokeTokenRequest.getToken());
```
A full JWT token in logs is a replay vulnerability: anyone with log access can reuse the token until it expires. Log only a hash or a truncated identifier.

---

### 5. No production CORS configuration
**File:** `src/main/java/the/chak/ecommerce/apigateway/SecurityConfig.java:48-51`  
**Severity:** Medium

`disabledCorsConfigurationSource()` registers an empty `CorsConfigurationSource` for all non-dev profiles. If the frontend is served from a different origin in production, all browser-initiated API calls will fail (no CORS headers are ever sent). An explicit, configurable production CORS config is needed (e.g. driven by `@ConfigurationProperties`).

---

## Architecture / Design Smells

### 6. `payments-service` route targets `price-service`
**Files:** `src/main/resources/application.yml:53-58`, `src/main/resources/application-dev.yml:66-69`  
**Severity:** Smell

The route with id `payments-service` points to `http://price-service:8080`. If payments are intentionally handled by `price-service`, rename the route id to eliminate confusion. If `payments-service` is a planned separate service, the current target is wrong.

---

### 7. Dead Ribbon configuration
**File:** `src/main/resources/application.yml:7-8`  
**Severity:** Smell

```yaml
spring.cloud.loadbalancer.ribbon.enabled: false
```
Ribbon was removed from Spring Cloud before version 2022.0.0. This property is silently ignored by Spring Cloud 2024.0.0 and should be deleted.

---

### 8. CORS configured in two layers simultaneously
**Files:** `src/main/resources/application-dev.yml` (Spring Cloud Gateway `globalcors`) and `SecurityConfig.java` (`CorsConfigurationSource` bean)  
**Severity:** Smell

In a WebFlux + Spring Security stack, the security CORS filter runs before the gateway's CORS filter, so the `CorsConfigurationSource` bean wins and the `globalcors` YAML block is redundant. Pick one authoritative source of CORS config.

---

### 9. `jjwt` pinned to deprecated 0.11.5 API
**Files:** `pom.xml`, `TokenUtils.java`, `TestJwtTokenGenerator.java`  
**Severity:** Smell

`Jwts.parserBuilder()` is deprecated since jjwt 0.12.0 in favour of `Jwts.parser()`. Bump to 0.12.x and migrate the call sites to avoid a future breaking change.

---

## Readability / Code Quality

### 10. Field `Uri` violates Java naming convention
**File:** `src/main/java/the/chak/ecommerce/apigateway/JwtConfig.java:11`

Field names must start with a lowercase letter. Rename `Uri` → `uri`.

---

### 11. Method `RedisRevokeFallback` violates Java naming convention
**File:** `src/main/java/the/chak/ecommerce/apigateway/control/TokenUtils.java:69`

Method names must start with a lowercase letter. Rename `RedisRevokeFallback` → `redisRevokeFallback`.

---

### 12. `CookieTokenResolver.java` is entirely commented-out dead code
**File:** `src/main/java/the/chak/ecommerce/apigateway/control/CookieTokenResolver.java`

The entire file is commented out. Delete it — version control preserves the history.

---

### 13. Mixed dependency injection styles
**Severity:** Inconsistency

`AuthenticationManager` and `ApiGatewayController` use constructor injection (correct). `TokenUtils`, `RsaKeyProvider`, `AuthHeaderTokenResolver`, and `SecurityContextRepository` use `@Autowired` field injection. Apply constructor injection uniformly.

---

### 14. Noisy `warn` log on every unauthenticated request
**File:** `src/main/java/the/chak/ecommerce/apigateway/control/SecurityContextRepository.java:40`

```java
log.warn("couldn't resolve token .. gonna ignore.");
```
This fires on every public endpoint hit, OPTIONS pre-flight, and actuator call. Downgrade to `log.debug(...)`.

---

### 15. Redundant `redis.start()` in test lifecycle
**File:** `src/test/java/the/chak/ecommerce/apigateway/SecurityConfigTest.java:39`

`@BeforeAll redis.start()` is redundant — `@Testcontainers` + static `@Container` already manages the full container lifecycle automatically.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug + Security | `AuthenticationManager.java:27,30` | Thread-local `SecurityContextHolder` in reactive context |
| 2 | Bug | `SecurityConfig.java:71` | Permit rule targets non-existent path `/api/featured-products/**` |
| 3 | Bug | `AuthHeaderTokenResolver.java:20` | `replace()` instead of `substring()` for prefix stripping |
| 4 | High Security | `ApiGatewayController.java:29` | Full JWT token logged at INFO level |
| 5 | Medium Security | `SecurityConfig.java:48` | No production CORS configuration |
| 6 | Smell | `application.yml:53` | `payments-service` route targets `price-service` |
| 7 | Smell | `application.yml:7` | Ribbon config property is dead |
| 8 | Smell | YAML + SecurityConfig | CORS defined in two layers |
| 9 | Smell | `pom.xml` / token files | `jjwt` 0.11.5 uses deprecated API |
| 10 | Naming | `JwtConfig.java:11` | Field `Uri` → `uri` |
| 11 | Naming | `TokenUtils.java:69` | Method `RedisRevokeFallback` → `redisRevokeFallback` |
| 12 | Dead code | `CookieTokenResolver.java` | Entire file is commented out — delete it |
| 13 | Style | Service-wide | Mixed `@Autowired` field vs constructor injection |
| 14 | Log quality | `SecurityContextRepository.java:40` | `warn` → `debug` for missing-token log |
| 15 | Test | `SecurityConfigTest.java:39` | Redundant `redis.start()` call |
