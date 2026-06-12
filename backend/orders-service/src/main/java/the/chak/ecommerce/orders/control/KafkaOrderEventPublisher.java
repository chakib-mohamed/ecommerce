package the.chak.ecommerce.orders.control;

import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.opentelemetry.context.Context;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;

/**
 * Sole owner of the {@code order-initiated} outgoing channel (SmallRye allows one emitter per
 * channel). The keyed {@code publishOrderInitiated} method is what {@link OutboxRelay} uses to drain
 * the outbox with a Kafka message key.
 */
@ApplicationScoped
public class KafkaOrderEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaOrderEventPublisher.class);

    @Inject
    @Channel("order-initiated")
    Emitter<OrderDTO> emitter;

    /**
     * Publishes an {@code order-initiated} event with the given Kafka message key, parenting the
     * producer span on {@code parent} (the originating request's trace). The returned future
     * completes when the broker acks (or completes exceptionally on nack).
     */
    public CompletableFuture<Void> publishOrderInitiated(OrderDTO order, String key, Context parent) {
        LOG.infof("Publishing order-initiated event orderId=%s userId=%s", order.getId(),
                order.getUserID());
        CompletableFuture<Void> ack = new CompletableFuture<>();
        // TracingMetadata.withCurrent carries the outbox-stored parent context on the message itself;
        // SmallRye's outgoing Kafka tracing reads getCurrentContext() as the producer span's parent,
        // so a relay publish stays in the request's trace even across the background-thread hop.
        Metadata metadata = Metadata.of(
                OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build(),
                TracingMetadata.withCurrent(parent));
        emitter.send(Message.of(order, metadata,
                () -> {
                    ack.complete(null);
                    return CompletableFuture.completedFuture(null);
                },
                throwable -> {
                    ack.completeExceptionally(throwable);
                    return CompletableFuture.completedFuture(null);
                }));
        return ack;
    }
}
