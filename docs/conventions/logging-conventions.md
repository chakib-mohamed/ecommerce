# Logging Conventions

Applies to all backend services: the five Quarkus services and the Spring Boot gateway.

---

## 1. Logger declaration

**Quarkus services** — use JBoss Logger (no SLF4J import needed, already on the classpath):
```java
private static final Logger LOG = Logger.getLogger(MyService.class);
```

**API Gateway** — use SLF4J via Lombok's `@Slf4j` annotation (already used project-wide):
```java
@Slf4j
@Component
public class MyComponent { }
// Then use: log.info(...)
```

---

## 2. Log levels

| Level | When to use | Example |
|-------|-------------|---------|
| `ERROR` | Unexpected failure requiring human attention — goes to `GlobalExceptionHandler` | Unhandled exception, Kafka publish failure |
| `WARN` | Known anomaly that was handled — service continues | Price-changed event for unknown productId |
| `INFO` | Normal business events worth auditing | Login success, order created, event published |
| `DEBUG` | Developer diagnostics — verbose, disabled in production | Internal state, intermediate values |
| `TRACE` | Per-field detail — almost never in production | Serialized payloads |

Default production level: `INFO`. Never lower a `GlobalExceptionHandler` ERROR to WARN.

---

## 3. Structured format — key=value pairs

All log messages must carry context as `key=value` pairs. This makes logs grep/jq-friendly without a log aggregator.

```java
// Good
LOG.infof("Order created orderId=%s userId=%s products=%d total=%.2f",
    order.getId(), order.getUserID(), order.getProducts().size(), order.getPrice());

// Bad — not parseable
LOG.info("Order was created for user " + userId + " total: " + total);
```

Standard key names used across services:

| Key | Type | Used for |
|-----|------|---------|
| `userId` | string | authenticated user identity |
| `productId` | UUID string | product identifier |
| `orderId` | string | order identifier |
| `traceId` | hex string | trace correlation ID across services — auto-stamped via MDC (see §4) |
| `spanId` | hex string | current span within the trace — auto-stamped via MDC (see §4) |
| `elapsed` | integer (ms) | duration of a cross-service call |
| `total` | decimal | monetary total |
| `items` / `products` | integer | count |

---

## 4. Correlation ID (traceId / spanId) via MDC

Every log line in a request thread carries a `traceId` and `spanId` so you can correlate logs
across services **and pivot straight into Jaeger**. These come from OpenTelemetry — there is no
hand-rolled correlation id to manage.

> **`X-Request-ID` is retired.** The old UUID `requestId` (minted at the gateway, forwarded as
> `X-Request-ID`, stamped into MDC by each service's filter) has been removed. `traceId` supersedes
> it: it is present in the MDC of *every* request regardless of sampling, and — unlike `requestId` —
> it links directly to the trace in Jaeger. Standardize on `traceId`.

> **Aggregated in Loki.** Beyond stdout, logs ship over OTLP to the Collector, which forwards them
> to **Grafana Loki** (Quarkus `quarkus.otel.logs.enabled=true`; the gateway via Spring Boot OTLP
> logging + the OTel Logback appender). `traceId`/`spanId` arrive as **structured metadata** — not
> re-parsed from text — so they stay queryable in LogQL and pivot both ways: log → trace (Loki
> `derivedFields` → Jaeger) and trace → log (Jaeger `tracesToLogsV2` → Loki). The key=value format
> below still applies; it is now also searchable via LogQL. The console handler is unchanged, so
> `make logs` keeps working. Details: `docs/specs/log-aggregation.md`.

### How it flows

1. **OpenTelemetry auto-instrumentation** (the `opentelemetry-*` libraries on the classpath) starts a
   span for every inbound request and propagates the W3C `traceparent` header across HTTP and Kafka —
   no client filter or header code needed.
2. **`traceId`/`spanId` land in MDC automatically.** The OTel logging integration populates them, so
   the log format only has to reference them — services do **not** `MDC.put(...)` them by hand.
3. **Request filters stay** — they still log method/path/userId (see §5) — but no longer touch any
   correlation id.
4. **Gateway echoes the trace id** back to the client as an `X-Trace-Id` response header, for a
   client-facing handle into the trace.

### Quarkus — log format

Every Quarkus service `application.properties` references the OTel-populated MDC keys:
```properties
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] traceId=%X{traceId} spanId=%X{spanId} %s%e%n
```

### Gateway

The gateway uses `micrometer-tracing-bridge-otel`, which bridges the active span's `traceId`/`spanId`
into the logging context, so its log pattern carries them the same way. The gateway also sets the
`X-Trace-Id` response header from the current span. (No MDC propagation plumbing is required — this
is handled by the tracing bridge, not by hand.)

---

## 5. Where logs belong — layer rules

| Layer | Rule |
|-------|------|
| `boundary/` resource classes | **No logging.** Resources are thin delegates; they do not own decisions. |
| `boundary/` filters (`@Provider`) | Log inbound requests only — method, path, userId. Nothing else (`traceId`/`spanId` are stamped automatically via MDC). |
| `control/` services | Log business outcomes: what was created/updated/deleted, what events were sent/received. |
| `control/` API clients | Log the outbound call before it happens and the outcome (status + elapsed time) after. |
| `entity/` | Never log. |

---

## 6. Elapsed time for cross-service calls

Every synchronous HTTP call to another service must log the elapsed time. A call that succeeds in 1800ms instead of the usual 20ms is a production incident waiting to happen.

```java
long start = System.currentTimeMillis();
Response response = pricingApiClient.calculatePrice(order);
LOG.infof("POST pricing-service /pricing/calculate status=%d elapsed=%dms",
    response.getStatus(), System.currentTimeMillis() - start);
```

This applies to: `ProductsApiClient`, `PricingApiClient` in orders-service, and any future REST client added to any service.

---

## 7. Kafka — symmetric logging

For every Kafka message, log on both the **produce side** and the **consume side** with the same identifier. This lets you confirm delivery and measure consumer lag.

```java
// Produce side (products-service)
LOG.infof("Publishing product-updated event productId=%s", event.getProduct().getUuid());
productUpdatedEmitter.send(event);

// Consume side (featured-products-service)
@Incoming("product-updated")
public void consumeProductUpdated(ProductUpdatedEvent event) {
    LOG.infof("Product-updated event received productId=%s", event.getProduct().getUuid());
    productService.onProductUpdated(event);
}
```

Consistent identifier used on both sides: `productId`, `orderId`, etc. — never just `event received`.

---

## 8. Health-check path suppression

Skip logging for health and metrics endpoints in every `RequestLoggingFilter`. These are polled every few seconds by orchestrators and drown out real traffic.

```java
private static final Set<String> SKIP_PATHS = Set.of("/q/health", "/q/metrics", "/q/ready", "/q/live");

if (SKIP_PATHS.stream().anyMatch(path::startsWith)) return;
```

For the gateway, skip `/actuator/**`.

---

## 9. Cache — log misses only

In `PriceCacheService` (or any future cache wrapper), log only cache misses. Cache hits are high-frequency and carry no signal at INFO level.

```java
Double cached = priceValues.get(key);
if (cached != null) {
    return cached;   // no log — hit is expected
}
LOG.infof("Price cache miss productId=%s — fetching from products-service", productId);
```

---

## 10. Startup — log @PostConstruct configuration state

Services that initialize from config (S3 bucket, Drools rules, Redis commands) should emit one INFO line at startup. This saves enormous debugging time when a service boots with the wrong config.

```java
@PostConstruct
void init() {
    // ... setup ...
    LOG.infof("S3 bucket ready bucket=%s endpoint=%s", bucketName, endpoint);
    LOG.infof("Drools rules loaded resource=%s", "the/chak/pricing/ApplySpecialOffers.drl");
}
```

---

## 11. Sensitive data — never log

| Data | Rule |
|------|------|
| Passwords / BCrypt hashes | Never, under any circumstances |
| JWT tokens | Log only first 8 chars as a reference prefix, or omit entirely |
| Email addresses | Acceptable in low-volume auth paths (login, signup) for audit trail |
| PII in high-volume paths | Avoid — e.g. do not log userId on every cache lookup |
| Stack traces at INFO | Never — stack traces belong in ERROR/WARN only |

---

## 12. Parameterized logging — no string concatenation

Both JBoss Logger (`LOG.infof`) and SLF4J (`log.info("... {}", value)`) defer string construction until the level is enabled. Never concatenate strings in log calls.

```java
// Good — JBoss Logger parameterized form
LOG.infof("Product created productId=%s title=%s", product.getUuid(), product.getTitle());

// Good — SLF4J parameterized form (gateway)
log.info("JWT authenticated userId={}", username);

// Bad — string built even when INFO is disabled
LOG.info("Product created: " + product.getUuid() + " " + product.getTitle());
```

---

## Summary checklist before merging a logging change

- [ ] Log is in control layer or a filter — not in a resource class
- [ ] Message uses `key=value` format
- [ ] No passwords, full tokens, or high-volume PII
- [ ] Cross-service calls include `elapsed` in ms
- [ ] Kafka events logged on both produce and consume sides with same identifier
- [ ] Health-check paths suppressed in any new filter
- [ ] `@PostConstruct` startup state logged where config is loaded
- [ ] Parameterized form used — no string concatenation
