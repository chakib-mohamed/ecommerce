package the.chak.ecommerce.products.control;

import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.OutboxEvent;

/**
 * Builds {@link OutboxEvent} rows from product domain events. The DB-stored payload is serialized
 * with a plain (camelCase) JSON-B instance purely as an internal round-trip format: {@link OutboxRelay}
 * reads it back with a matching plain JSON-B before publishing. This stored form is never the Kafka
 * wire format - the relay re-serializes the event through the channel's {@code JsonbSerializer} (the
 * CDI-managed JSON-B), so the on-the-wire payload is snake_case like the rest of the platform.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_PRODUCT = "product";
    static final String TOPIC_PRODUCT_UPDATED = "product-updated";
    static final String TOPIC_PRODUCT_DELETED = "product-deleted";

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

    public OutboxEvent productUpdated(UUID aggregateId, ProductUpdatedEvent event) {
        return build(aggregateId, TOPIC_PRODUCT_UPDATED, event);
    }

    public OutboxEvent productDeleted(UUID aggregateId, ProductDeletedEvent event) {
        return build(aggregateId, TOPIC_PRODUCT_DELETED, event);
    }

    private OutboxEvent build(UUID aggregateId, String topic, Object payload) {
        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(AGGREGATE_TYPE_PRODUCT);
        row.setAggregateId(aggregateId);
        row.setEventType(topic);
        row.setTopic(topic);
        row.setPayload(jsonb.toJson(payload));
        return row;
    }
}
