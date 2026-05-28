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

Every class named `*Service` in a `control/` package must have **100% line coverage**.
JaCoCo enforces this at `mvn verify` — the build fails if any line in a matching class is uncovered.

**In scope** (caught by the `*Service` name pattern):
- Domain services: `UserService`, `CartService`, `OrderService`, `PriceService`, `ApplyPromotionsService`, etc.
- Infrastructure services that follow the `*Service` naming convention: `StorageService`, `PriceCacheService`, etc.

**Out of scope** (not named `*Service`):
- Kafka producers/consumers: `KafkaEventPublisher`, `KafkaEventConsumer`, `PriceChangedConsumer`
- REST clients: `ProductsApiClient`, `PricingApiClient`
- Initializers: `CartIndexInitializer`, `PriceIndexInitializer`
- Event payloads, exceptions, value objects, config classes

When a public method cannot be covered without excessive test complexity (e.g. a private helper path that is genuinely unreachable), prefer extracting the uncoverable logic into a separate class rather than suppressing the rule.

## Control Layer Unit Tests

**Rule**: Control-layer services (`*Service` in `control/` packages) must use **plain JUnit 5 + Mockito unit tests**, not `@QuarkusTest`. This avoids container spin-up overhead and keeps unit tests focused on business logic.

### Test Setup Pattern

Use `@ExtendWith(MockitoExtension.class)` instead of Quarkus test annotations:

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    UserService sut;

    @Mock
    EntityManager em;

    MockedStatic<User> mockedUser;

    @BeforeEach
    void setUp() {
        mockedUser = mockStatic(User.class);
    }

    @AfterEach
    void teardown() {
        mockedUser.close();
    }
}
```

### Mocking Panache Queries

For services using Panache (MongoDB or JPA), mock static query methods with `MockedStatic`:

```java
// Mock a Panache query
PanacheQuery<User> query = mock(PanacheQuery.class);
when(query.list()).thenReturn(List.of(user));
mockedUser.when(() -> User.find(anyString(), any(Map.class))).thenReturn(query);
```

### When to Keep Integration Tests

**Boundary tests** (`*ResourceTest` in `boundary/` packages) stay as `@QuarkusTest` integration tests because they:
- Test full HTTP stack behavior
- Require real database state
- Verify API contracts end-to-end

**Keep Testcontainers only for**:
- Boundary/resource tests (REST API validation)
- Kafka consumer integration tests
- Health check and probe tests

### Rationale

- **Speed**: Unit tests run in milliseconds; integration tests take seconds per service
- **Isolation**: Mock external dependencies; test service logic in isolation
- **Repeatability**: No container state pollution between test runs
- **Cost**: Reduces Docker load during parallel test runs (no Testcontainers per unit test)

Example: A service with 10 unit tests runs 10ms total vs. 100s with `@QuarkusTest` (containers + startup overhead).

### Exception: Zero-Dependency Services

Services with no external dependencies (pure logic, no I/O) can use plain `new ServiceClass()` in `@BeforeEach`:

```java
@BeforeEach
void setUp() {
    applyPromotionsService = new ApplyPromotionsService();
}
```

No mocking framework needed for stateless calculation services.
