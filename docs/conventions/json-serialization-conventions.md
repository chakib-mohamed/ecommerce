# JSON Serialization Conventions

Applies to all Quarkus services. The project-wide wire contract (snake_case fields, omitted
nulls, ISO-8601 dates, unknown keys ignored) is stated in the root `CLAUDE.md`. This doc covers
**how each framework is configured** to produce that contract.

## JSON-B services (all Quarkus HTTP endpoints)

Both the naming strategy and null omission must be set via `CustomJsonbConfigCustomizer` in `boundary/` — there are no equivalent `application.properties` keys for these in Quarkus 3.17.6:
```java
@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {
    @Override
    public void customize(JsonbConfig config) {
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
        config.withNullValues(false);
    }
}
```

JSON-B defaults to ISO-8601 dates — no extra config needed.

## Jackson services (REST clients in shared API modules, price-service HTTP)

Configure in `application.properties`:
```properties
quarkus.jackson.property-naming-strategy=SNAKE_CASE
quarkus.jackson.serialization-inclusion=NON_NULL
quarkus.jackson.write-dates-as-timestamps=false
quarkus.jackson.fail-on-unknown-properties=false
```

## Kafka events

`JsonbSerializer` and `JsonbDeserializer` (used in all Kafka channels) resolve the **CDI-managed
`Jsonb`** bean — the same one `CustomJsonbConfigCustomizer` configures. Kafka payloads therefore
follow the project-wide `LOWER_CASE_WITH_UNDERSCORES` strategy and are **snake_case on the wire**,
exactly like HTTP JSON (e.g. `productId` → `product_id`, `newPrice` → `new_price`, `productUuid`
→ `product_uuid`). Java fields in `control/events/` classes stay camelCase (normal Java); only the
serialized names are snake_case. Producers and consumers share this strategy, so the formats match
on both sides. Verified on Quarkus 3.17.6 — an earlier note claimed these serializers used a
separate plain Jsonb instance and stayed camelCase; that is not the case.

The transactional outbox follows the same rule for its **stored** payload: the `OutboxEventFactory`
serializes the event body with the injected CDI `Jsonb`, and the relay reads it back with that same
bean before publishing. So the at-rest payload is snake_case too — at-rest == wire == snake_case,
with a single configured serializer and no separate internal format.

## Annotation exceptions

`@JsonbProperty` / `@JsonProperty` are only permitted to override the naming for a specific field that must deviate from the global strategy (rare). Never add them just to reproduce what the strategy already does.
