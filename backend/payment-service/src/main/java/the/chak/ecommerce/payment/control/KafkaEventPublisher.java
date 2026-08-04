package the.chak.ecommerce.payment.control;

import java.util.concurrent.CompletableFuture;
import io.opentelemetry.context.Context;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;

/** Sole owner of this service's outgoing channels; {@link OutboxRelay} drains the outbox through it. */
@ApplicationScoped
public class KafkaEventPublisher {

    @Inject
    @Channel("payment-captured")
    Emitter<PaymentCapturedEvent> capturedEmitter;

    @Inject
    @Channel("payment-failed")
    Emitter<PaymentFailedEvent> failedEmitter;

    public CompletableFuture<Void> publishPaymentCaptured(
            PaymentCapturedEvent event, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        capturedEmitter.send(keyedMessage(event, key, parent, ack));
        return ack;
    }

    public CompletableFuture<Void> publishPaymentFailed(
            PaymentFailedEvent event, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        failedEmitter.send(keyedMessage(event, key, parent, ack));
        return ack;
    }

    /**
     * A payload wrapped with its Kafka key and the trace it belongs to.
     *
     * <p>The relay publishes on a background thread after the request that produced the row has
     * ended, so the producer span is re-parented onto the originating trace rather than starting a
     * new one - see {@code OutboxTracing}.
     */
    private <T> Message<T> keyedMessage(T payload, String key, Context parent,
            CompletableFuture<Void> ack) {
        return Message.of(payload)
                .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(key).build())
                .addMetadata(TracingMetadata.withPrevious(parent))
                .withAck(() -> {
                    ack.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(throwable -> {
                    ack.completeExceptionally(throwable);
                    return CompletableFuture.completedFuture(null);
                });
    }
}
