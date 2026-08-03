package the.chak.ecommerce.products.control;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import the.chak.ecommerce.outbox.OutboxTracing;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.control.events.StockRejectedEvent;
import the.chak.ecommerce.products.control.events.StockReservedEvent;
import the.chak.ecommerce.products.entity.OutboxEvent;

/**
 * Builds {@link OutboxEvent} rows from product domain events. The stored payload is serialized with
 * the CDI-managed JSON-B - the same instance the Kafka channel's {@code JsonbSerializer} uses - so
 * the at-rest form is already snake_case, identical to the wire. {@link OutboxRelay} reads it back
 * with that same JSON-B before publishing; there is no separate internal format.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_PRODUCT = "product";
    static final String TOPIC_PRODUCT_UPDATED = "product-updated";
    static final String TOPIC_PRODUCT_DELETED = "product-deleted";
    static final String AGGREGATE_TYPE_ORDER = "order";
    static final String TOPIC_STOCK_RESERVED = "stock-reserved";
    static final String TOPIC_STOCK_REJECTED = "stock-rejected";

    @Inject
    Jsonb jsonb;

    public OutboxEvent productUpdated(UUID aggregateId, ProductUpdatedEvent event) {
        return build(aggregateId.toString(), TOPIC_PRODUCT_UPDATED, event);
    }

    public OutboxEvent productDeleted(UUID aggregateId, ProductDeletedEvent event) {
        return build(aggregateId.toString(), TOPIC_PRODUCT_DELETED, event);
    }

    /**
     * Saga replies are keyed by order, not product: the orchestrator consumes them per order, and
     * the key is what keeps one order's replies in the sequence they were produced.
     */
    public OutboxEvent stockReserved(String orderId, StockReservedEvent event) {
        return build(AGGREGATE_TYPE_ORDER, orderId, TOPIC_STOCK_RESERVED, event);
    }

    public OutboxEvent stockRejected(String orderId, StockRejectedEvent event) {
        return build(AGGREGATE_TYPE_ORDER, orderId, TOPIC_STOCK_REJECTED, event);
    }

    private OutboxEvent build(String aggregateId, String topic, Object payload) {
        return build(AGGREGATE_TYPE_PRODUCT, aggregateId, topic, payload);
    }

    private OutboxEvent build(String aggregateType, String aggregateId, String topic, Object payload) {
        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(topic);
        row.setTopic(topic);
        row.setPayload(jsonb.toJson(payload));
        row.setTraceparent(OutboxTracing.currentTraceparent());
        return row;
    }
}
