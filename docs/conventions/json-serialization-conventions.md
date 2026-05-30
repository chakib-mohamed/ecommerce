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

`JsonbSerializer` and `JsonbDeserializer` (used in all Kafka channels) create their own plain Jsonb instance, independent of the CDI-managed one. Kafka event field names therefore remain camelCase regardless of the JSON-B naming strategy configured for HTTP. Do not rely on `LOWER_CASE_WITH_UNDERSCORES` applying to Kafka payloads — use consistent camelCase in `control/events/` classes.

## Annotation exceptions

`@JsonbProperty` / `@JsonProperty` are only permitted to override the naming for a specific field that must deviate from the global strategy (rare). Never add them just to reproduce what the strategy already does.
