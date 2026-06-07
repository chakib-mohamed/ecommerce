package the.chak.ecommerce.pricing.control;

import java.time.Instant;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.entity.OutboxEntry;

/**
 * Builds {@link OutboxEntry} documents from price domain events. The stored payload is serialized
 * with the CDI-managed JSON-B - the same instance the Kafka channel's {@code JsonbSerializer} uses -
 * so the at-rest form is already snake_case, identical to the wire. {@link OutboxRelay} reads it back
 * with that same JSON-B before publishing; there is no separate internal format.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_PRICE = "price";
    static final String TOPIC_PRICE_CHANGED = "price-changed";

    @Inject
    Jsonb jsonb;

    public OutboxEntry priceChanged(String productId, PriceChangedEvent event) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = AGGREGATE_TYPE_PRICE;
        entry.aggregateId = productId;
        entry.eventType = TOPIC_PRICE_CHANGED;
        entry.topic = TOPIC_PRICE_CHANGED;
        entry.payload = jsonb.toJson(event);
        entry.createdAt = Instant.now();
        return entry;
    }
}
