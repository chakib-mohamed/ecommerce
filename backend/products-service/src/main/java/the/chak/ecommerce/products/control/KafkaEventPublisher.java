package the.chak.ecommerce.products.control;

import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import io.opentelemetry.context.Context;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

/**
 * Sole owner of the {@code product-updated} / {@code product-deleted} outgoing channels (SmallRye
 * allows one emitter per channel). The keyed {@code publish*} methods are what {@link OutboxRelay}
 * uses to drain the outbox with a Kafka message key. There is no longer a direct
 * {@code emitter.send(event)} publish path - every event reaches Kafka through the outbox relay.
 */
@ApplicationScoped
public class KafkaEventPublisher {

    @Inject
    @Channel("product-updated")
    Emitter<ProductUpdatedEvent> productUpdatedEmitter;


    @Inject
    @Channel("product-deleted")
    Emitter<ProductDeletedEvent> productDeletedEmitter;

    @Inject
    OutboxRelay relay;

    /**
     * Post-commit wake-up: an outbox row was appended, so nudge the relay to publish it now rather
     * than waiting for the next scheduled tick. Best-effort - if this signal is lost (e.g. a crash
     * before it runs), the scheduled tick still delivers the row.
     */
    public void onOutboxAppended(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) OutboxAppended signal) {
        relay.requestPoll();
    }

    /**
     * Publishes a {@code product-updated} event with the given Kafka message key, parenting the
     * producer span on {@code parent} (the originating request's trace). The returned future
     * completes when the broker acks (or completes exceptionally on nack).
     */
    public CompletableFuture<Void> publishProductUpdated(
            ProductUpdatedEvent event, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        productUpdatedEmitter.send(keyedMessage(event, key, parent, ack));
        return ack;
    }

    /**
     * Publishes a {@code product-deleted} event with the given Kafka message key, parenting the
     * producer span on {@code parent} (the originating request's trace). The returned future
     * completes when the broker acks (or completes exceptionally on nack).
     */
    public CompletableFuture<Void> publishProductDeleted(
            ProductDeletedEvent event, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        productDeletedEmitter.send(keyedMessage(event, key, parent, ack));
        return ack;
    }

    private <T> Message<T> keyedMessage(T payload, String key, Context parent,
            CompletableFuture<Void> ack) {
        // TracingMetadata.withCurrent carries the outbox-stored parent context on the message itself;
        // SmallRye's outgoing Kafka tracing reads getCurrentContext() as the producer span's parent,
        // so a relay publish stays in the request's trace even across the background-thread hop.
        Metadata metadata = Metadata.of(
                OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build(),
                TracingMetadata.withCurrent(parent));
        return Message.of(payload, metadata,
                () -> {
                    ack.complete(null);
                    return CompletableFuture.completedFuture(null);
                },
                throwable -> {
                    ack.completeExceptionally(throwable);
                    return CompletableFuture.completedFuture(null);
                });
    }
}
