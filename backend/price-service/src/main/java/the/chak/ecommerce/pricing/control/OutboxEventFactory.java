package the.chak.ecommerce.pricing.control;

import java.time.Instant;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.entity.OutboxEntry;

/**
 * Builds {@link OutboxEntry} documents from price domain events. The stored payload is serialized
 * with a plain (camelCase) JSON-B instance purely as an internal round-trip format: {@link OutboxRelay}
 * reads it back with a matching plain JSON-B before publishing. This stored form is never the Kafka
 * wire format - the relay re-serializes the event through the channel's {@code JsonbSerializer} (the
 * CDI-managed JSON-B), so the on-the-wire payload is snake_case like the rest of the platform.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_PRICE = "price";
    static final String TOPIC_PRICE_CHANGED = "price-changed";

    private static final Logger LOG = Logger.getLogger(OutboxEventFactory.class);

    private Jsonb jsonb;

    @PostConstruct
    void init() {
        jsonb = JsonbBuilder.create();
    }

    @PreDestroy
    void close() {
        try {
            jsonb.close();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to close JSON-B");
        }
    }

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
