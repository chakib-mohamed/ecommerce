package the.chak.ecommerce.payment.control;

import java.time.Instant;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;
import the.chak.ecommerce.payment.entity.OutboxEvent;
import the.chak.ecommerce.outbox.OutboxTracing;

/** Builds the outbox rows carrying this service's replies. Keyed by order id, like every saga message. */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_PAYMENT = "payment";
    static final String TOPIC_PAYMENT_CAPTURED = "payment-captured";
    static final String TOPIC_PAYMENT_FAILED = "payment-failed";

    @Inject
    Jsonb jsonb;

    public OutboxEvent paymentCaptured(String orderId, PaymentCapturedEvent payload) {
        return build(orderId, TOPIC_PAYMENT_CAPTURED, payload);
    }

    public OutboxEvent paymentFailed(String orderId, PaymentFailedEvent payload) {
        return build(orderId, TOPIC_PAYMENT_FAILED, payload);
    }

    private OutboxEvent build(String orderId, String topic, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType(AGGREGATE_TYPE_PAYMENT);
        event.setAggregateId(orderId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload(jsonb.toJson(payload));
        event.setTraceparent(OutboxTracing.currentTraceparent());
        event.setCreatedAt(Instant.now());
        return event;
    }
}
