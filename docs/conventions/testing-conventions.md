# Testing Conventions

Applies to all Quarkus services. See `backend/CLAUDE.md` for infrastructure rules (Testcontainers, Kafka test setup).

## Test Method Naming

`action_context_expectedOutcome` — three underscore-separated camelCase segments, no `test` prefix, no `public` modifier.

```java
// good
void createOrder_validProducts_returns201WithCalculatedPrice()
void authenticate_wrongPassword_returns401()
void getUsername_revokedToken_returnsEmpty()

// bad
public void testCreateOrder()
void testAuthenticate_WrongPassword_Returns401()
```

## Test Body Structure

Explicit `// given`, `// when`, `// then` comment blocks in every test. Omit `// given` only when there is genuinely no setup.

```java
@Test
void confirmOrder_initiatedOrder_changesStatusToConfirmed() {
    // given
    Order order = new Order();
    order.setStatus(OrderStatus.INITIATED);
    order.persist();

    // when
    var response = given().when().post("/orders/" + order.id + "/confirm");

    // then
    response.then().statusCode(200).body("status", is("CONFIRMED"));
}
```

## HTTP Test Split

Separate the HTTP call from the assertions so `// when` and `// then` are distinct.

```java
// when
var response = given().contentType(ContentType.JSON).body(request)
        .when().post("/orders");

// then
response.then().statusCode(201).body("price", is(100.0f));
```

## One Behavior Per Test

Do not combine create + update + delete in a single test method. Each test exercises exactly one scenario. Compound setup (e.g. creating a record before testing an update) belongs in `// given`, not as a separate test phase.

## Class Modifiers

Test classes are package-private (no `public`).

## Coverage

Two class groups must have **100% branch coverage**:
- `control/*Service`
- `boundary/*Resource` and `boundary/*Controller`

JaCoCo enforces this at `mvn verify` (the `check` rule in `backend/pom.xml`, `BRANCH` counter at
`1.0`) — the build fails if any branch in a matching class is uncovered.

**In scope** (caught by the name patterns):
- Domain services: `UserService`, `CartService`, `OrderService`, `PriceService`, `ApplyPromotionsService`, etc.
- Infrastructure services that follow the `*Service` naming convention: `StorageService`, `PriceCacheService`, etc.
- REST endpoints: `*Resource` and `*Controller` classes in `boundary/`.

**Out of scope** (not matched by the patterns):
- Kafka producers/consumers: `KafkaEventPublisher`, `KafkaEventConsumer`, `PriceChangedConsumer`
- REST clients: `ProductsApiClient`, `PricingApiClient`
- Initializers: `CartIndexInitializer`, `PriceIndexInitializer`
- Event payloads, exceptions, value objects, config classes

When a public method cannot be covered without excessive test complexity (e.g. a private helper path that is genuinely unreachable), prefer extracting the uncoverable logic into a separate class rather than suppressing the rule.

## Control Layer Unit Tests

**Rule**: Control-layer services (`*Service` in `control/` packages) must use **plain JUnit 5 + Mockito unit tests**, not `@QuarkusTest`. This avoids container spin-up overhead and keeps unit tests focused on business logic. Enforced by `ControlTestRulesArchTest` (ArchUnit) in every service — a `@QuarkusTest` on a `control/*ServiceTest` fails the build.

### Test Setup Pattern

Use `@ExtendWith(MockitoExtension.class)` instead of Quarkus test annotations. Inject the service
with `@InjectMocks` and its collaborators with `@Mock`:

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    @Test
    void addUser_newEmail_hashesPasswordAndPersists() {
        // given
        User user = new User();
        user.email = "test@example.com";
        user.password = "password123";
        when(userRepository.countByEmail(user.email)).thenReturn(0L);

        // when
        User result = userService.addUser(user);

        // then
        assertTrue(BCrypt.checkpw("password123", result.password));
        verify(userRepository).persistOrUpdate(user);
    }
}
```

### Mocking Panache Data Access

Panache data access goes through an injected `*Repository` bean (`UserRepository`,
`OrderRepository`, `CartRepository`, …), not static entity methods. Mock the repository like any
other collaborator — stub its query methods with `when(...)` and assert writes with `verify(...)`.
No `MockedStatic` is needed.

### When to Keep Integration Tests

**Boundary tests** (`*ResourceTest` in `boundary/` packages) stay as `@QuarkusTest` integration
tests — they exercise the full HTTP stack, real database state, and end-to-end API contracts.
Testcontainers (and the Kafka test resource) apply to these tests only; see `backend/CLAUDE.md`
for the infrastructure rules.

**Rationale**: control unit tests run in-process in milliseconds with no container spin-up, and
mock collaborators to test business logic in isolation without cross-run container state.

### Exception: Zero-Dependency Services

Services with no external dependencies (pure logic, no I/O) can use plain `new ServiceClass()` in `@BeforeEach`:

```java
@BeforeEach
void setUp() {
    applyPromotionsService = new ApplyPromotionsService();
}
```

No mocking framework needed for stateless calculation services.

## QuarkusTest Performance: One Application Boot Per Service

Quarkus reuses a single running application across all `@QuarkusTest` classes — **but only while
the test configuration is identical**. A class that declares a different set of
`@QuarkusTestResource`s, adds a `@TestProfile`, or overrides config forces Quarkus to shut down and
restart the application. Each restart costs seconds, and restarts are the dominant cost of the suite.

**Rule**: within a service, every `@QuarkusTest` class must use the **identical** set of
`@QuarkusTestResource`s — the full union the service needs (e.g. `MongoTestResource` +
`KafkaTestResource`). Declare that same set on every `@QuarkusTest` class, even one that exercises
only part of it. Never give one class a subset and another a superset — that mismatch alone forces
an extra boot.

```java
// bad — different resource sets across classes → Quarkus restarts between them
@QuarkusTest @QuarkusTestResource(MongoTestResource.class) @QuarkusTestResource(KafkaTestResource.class)
class OrdersResourceTest { }

@QuarkusTest @QuarkusTestResource(MongoTestResource.class) @QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)   // extra resource → second boot
class HealthCheckTest { }
```

To keep the set identical in one place, centralize it: a shared abstract base class carrying the
`@QuarkusTestResource` annotations that every `@QuarkusTest` extends, or a custom meta-annotation
(`@QuarkusTest` + the resources) applied to each class. The set then changes once, not per file.

Avoid `@TestProfile` and per-class config overrides unless a scenario genuinely needs a different
configuration — an isolated profile is a deliberate extra app boot, not a default.
