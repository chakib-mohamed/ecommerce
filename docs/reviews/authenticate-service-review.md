# Code Review: `authenticate-service`

> Reviewed: 2026-05-20

## Bugs

### 1. NPE on missing Authorization cookie
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:110`  
**Severity:** Bug

`@CookieParam(HttpHeaders.AUTHORIZATION) Cookie cookie` is `null` when the caller sends no Authorization cookie. Line 110 calls `cookie.getValue()` unconditionally, producing a `NullPointerException`. Add a null guard and return 401.

```java
if (cookie == null) return Response.status(Response.Status.UNAUTHORIZED).build();
```

---

### 2. `replace()` instead of `substring()` for prefix stripping
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:112`  
**Severity:** Bug

Same bug as in the gateway: `token.replace(jwtConfig.getPrefix(), "")` replaces every occurrence of `"Bearer "` in the token string. Should be:

```java
token = token.substring(jwtConfig.getPrefix().length());
```

---

### 3. No duplicate-email check on sign-up
**File:** `src/main/java/the/chak/ecommerce/authentication/control/UserService.java:35-40`  
**Severity:** Bug

`addUser()` calls `user.persist()` without checking whether the email already exists. MongoDB has no unique index here, so two accounts with the same email can be created. `findUser()` returns `firstResult()`, silently hiding the second. Add a pre-persist existence check and return a meaningful error (409 Conflict) on duplicate.

---

### 4. Unhandled `JwtException` in `TokenUtils.getUsername()`
**File:** `src/main/java/the/chak/ecommerce/authentication/control/TokenUtils.java:14-19`  
**Severity:** Bug

`Jwts.parserBuilder().parseClaimsJws(token)` throws `JwtException` (a `RuntimeException`) for any invalid, expired, or tampered token. The method has no try-catch, and its only caller (`resolveUserLogin`) doesn't catch it either. A bad cookie value will produce an uncontrolled 500 instead of a 401.

---

## Security

### 5. User enumeration via unauthenticated `GET /users/{email}`
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:62-71`  
**Severity:** High

`getUser()` has no authentication requirement. Any client can probe arbitrary email addresses and get a 200/404 response, confirming whether an account exists. This endpoint should either require authentication or be removed entirely — the gateway should be forwarding user-lookup to the same authenticated caller only.

---

### 6. No input validation on `AuthenticateRequest` and `SignUpRequest`
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/dto/AuthenticateRequest.java`, `SignUpRequest.java`  
**Severity:** High

Neither DTO has any validation annotations (`@NotBlank`, `@Email`, etc.). A null or empty email/password passes straight through to `User.find()` and `BCrypt.checkpw()`. `BCrypt.checkpw(null, hash)` throws `IllegalArgumentException` (unhandled 500). Add `@NotBlank` and `@Email` to both DTOs and enable Bean Validation on the resource (`@Valid`).

---

### 7. Auth cookie missing `Secure` and `SameSite` attributes
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:95-97`  
**Severity:** Medium

```java
new NewCookie.Builder("Authorization").value(token).path("/").httpOnly(true).build();
```

`httpOnly(true)` is set but `secure(true)` is missing. Without the `Secure` flag, the browser will transmit the JWT cookie over plain HTTP, enabling interception. Also missing a `SameSite=Strict` or `SameSite=Lax` policy, leaving the cookie vulnerable to CSRF.

---

### 8. BCrypt timing side-channel on unknown users
**File:** `src/main/java/the/chak/ecommerce/authentication/control/UserService.java:18-19`  
**Severity:** Low

```java
boolean passwordMatched = user != null && BCrypt.checkpw(...);
```

When `user == null`, the `&&` short-circuits and skips BCrypt entirely, making the response ~100 ms faster. An attacker can distinguish "unknown email" from "wrong password" purely by timing. Fix: always run `BCrypt.checkpw` against a dummy hash when the user is not found.

---

## Architecture

### 9. Banned reactive REST extension in use
**File:** `pom.xml:41-43`  
**Severity:** High

`quarkus-rest` is listed as a dependency. `backend/CLAUDE.md` explicitly bans `quarkus-rest` (the reactive REST layer) in all Quarkus services. Replace with `quarkus-resteasy` (blocking JAX-RS) and drop `quarkus-rest-jsonb` / `quarkus-rest-jackson` in favour of `quarkus-resteasy-jsonb`.

---

### 10. Both `quarkus-rest-jsonb` and `quarkus-rest-jackson` are declared
**File:** `pom.xml:53-54, 75-77`  
**Severity:** Medium

Having two JSON providers for the same REST layer causes serialization ambiguity. Since the service uses JSON-B (`@Jsonb*` annotations), drop `quarkus-rest-jackson`.

---

### 11. JWT token creation lives in the resource class
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:99-107`  
**Severity:** Smell

`createAccessToken()` is a private method inside the boundary/resource layer. Token creation is a domain concern and belongs in `TokenUtils` (or a dedicated `TokenService`). The resource class should call a service method, not build JWTs directly.

---

### 12. MongoDB queries bypass Panache's query API
**File:** `src/main/java/the/chak/ecommerce/authentication/control/UserService.java:14-32`  
**Severity:** Smell

Both `authenticateUser()` and `findUser()` build raw `Document` objects for queries instead of using Panache's named-param syntax:

```java
// Instead of:
Document doc = new Document(); doc.put("email", email); User.find(doc).firstResult();

// Use:
User.find("email", email).firstResult();
```

The raw `Document` approach is harder to read and bypasses Panache's query-builder benefits.

---

### 13. Missing `GlobalExceptionHandler`
**Severity:** Smell

Per the project convention documented in `backend/CLAUDE.md`, every Quarkus service must have a `GlobalExceptionHandler` in `boundary/` with `@ServerExceptionMapper` for both `FunctionalException` and `Exception`. This service has none, so all unhandled exceptions (JWT parse errors, MongoDB failures, NPEs) leak as unstructured 500 responses.

---

## Readability / Code Quality

### 14. Field `Uri` violates Java naming convention
**File:** `src/main/java/the/chak/ecommerce/authentication/control/JwtConfig.java:11`

Same issue as in the gateway: field `Uri` should be `uri`.

---

### 15. Package-private fields in `UserResponse`
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/dto/UserResponse.java:8-9`

`email` and `id` have no access modifier, making them package-private. With `@Data` they get getters/setters, but the fields themselves should be `private`.

---

### 16. Confusing `Response.ok(...).status(201)` pattern
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java:92`

```java
return Response.ok(this.userMapper.toUserResponse(user)).status(201).build();
```

`.ok()` sets status 200, then `.status(201)` overrides it. Use the explicit form instead:

```java
return Response.status(Response.Status.CREATED).entity(userMapper.toUserResponse(user)).build();
```

---

### 17. Missing `@Consumes` and inconsistent `@Produces` on resource
**File:** `src/main/java/the/chak/ecommerce/authentication/boundary/AuthenticationResource.java`

`@Produces(MediaType.APPLICATION_JSON)` is only on `authenticate()`. `getUser()`, `getAuthenticatedUser()`, and `signUp()` are missing it. Move both `@Produces` and `@Consumes(MediaType.APPLICATION_JSON)` to the class level.

---

### 18. Test: no coverage for wrong-password or duplicate-email scenarios at the API level
**File:** `src/test/java/the/chak/ecommerce/authentication/boundary/AuthenticationResourceTest.java`

`AuthenticationResourceTest` only tests the happy path (sign-up + login). It has no tests for wrong password (expect 401), duplicate email (expect 409), or missing/invalid input (expect 400). `UserServiceTest` covers the wrong-password case at the unit level, but the API-level contract is untested.

---

## Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | Bug | `AuthenticationResource.java:110` | NPE when Authorization cookie is absent |
| 2 | Bug | `AuthenticationResource.java:112` | `replace()` instead of `substring()` for prefix strip |
| 3 | Bug | `UserService.java:35` | No duplicate-email check; allows multiple accounts per email |
| 4 | Bug | `TokenUtils.java:14` | Unhandled `JwtException` propagates as 500 |
| 5 | High Security | `AuthenticationResource.java:62` | Unauthenticated `GET /users/{email}` enables user enumeration |
| 6 | High Security | DTOs | No input validation — null email/password cause unhandled exceptions |
| 7 | Medium Security | `AuthenticationResource.java:95` | Auth cookie missing `Secure` and `SameSite` attributes |
| 8 | Low Security | `UserService.java:18` | BCrypt timing side-channel leaks user-existence |
| 9 | High Arch | `pom.xml:41` | Banned `quarkus-rest` (reactive) in use — must use `quarkus-resteasy` |
| 10 | Medium Arch | `pom.xml` | Both `quarkus-rest-jsonb` and `quarkus-rest-jackson` declared |
| 11 | Smell | `AuthenticationResource.java:99` | JWT creation belongs in `TokenUtils`, not the resource class |
| 12 | Smell | `UserService.java:14,25` | Raw `Document` queries instead of Panache query API |
| 13 | Smell | Service-wide | Missing `GlobalExceptionHandler` |
| 14 | Naming | `JwtConfig.java:11` | Field `Uri` → `uri` |
| 15 | Style | `UserResponse.java:8-9` | Package-private fields should be `private` |
| 16 | Style | `AuthenticationResource.java:92` | `Response.ok().status(201)` — use `Response.status(CREATED)` |
| 17 | Style | `AuthenticationResource.java` | `@Produces`/`@Consumes` missing or inconsistent |
| 18 | Test | `AuthenticationResourceTest.java` | No negative-path API-level tests |
