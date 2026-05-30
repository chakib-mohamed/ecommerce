# Exception Handling Conventions

Applies to all Quarkus services.

## Exception Handling

All Quarkus services use a two-tier exception model. **Never use try-catch in resource classes.**

**Functional exceptions** — known domain errors with a specific HTTP status:
- Extend `FunctionalException` (abstract base in `control/exceptions/`, carries `Response.Status` and `errorCode`)
- Constructor signature: `(Response.Status status, String errorCode, String message)`
- All exception classes live in `control/exceptions/` when a service has more than one
- Examples: `CartNotFoundException` (404, `CART_NOT_FOUND`), `CartEmptyException` (400, `CART_EMPTY`)
- One class per error case; name describes the domain problem, not the HTTP code
- `errorCode` is a SCREAMING_SNAKE_CASE string unique within the service — the frontend uses it to identify errors

**Technical exceptions** — unexpected infrastructure or programming errors:
- Let them propagate as `RuntimeException`; the global handler logs and returns 500

**Global handler** — one `GlobalExceptionHandler` in `boundary/` per service:
- `FunctionalException` → `{ type: "FUNCTIONAL", errorCode, message }` + correct status
- `Exception` → `{ type: "TECHNICAL", errorCode: "INTERNAL_ERROR", message: "An unexpected error occurred" }` + logs

**`ErrorResponse`** DTO lives in `boundary/dto/`: `{ String type, String errorCode, String message }`.

## Boundary Validation

Annotate request DTOs with Bean Validation constraints (`@NotNull`, `@Positive`, `@Size`, etc.) and use `@Valid` on the resource method parameter. Use constraints for structural/format rules (null checks, numeric ranges, string patterns). Reserve `FunctionalException` subclasses for domain errors that depend on application state (e.g., duplicate email, insufficient stock).

When a service uses `@Valid` on a resource method, Resteasy intercepts `ConstraintViolationException` before `GlobalExceptionHandler` sees it. Add a dedicated `ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException>` in `boundary/` that returns `{ type: "FUNCTIONAL", errorCode: "VALIDATION_ERROR", message }` + 400. Services that do not use `@Valid` at the resource layer (e.g. `products-service`) do not need this second mapper — the `instanceof ConstraintViolationException` branch in `GlobalExceptionHandler` suffices.
