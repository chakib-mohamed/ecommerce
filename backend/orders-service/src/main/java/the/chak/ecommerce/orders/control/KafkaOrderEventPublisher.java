package the.chak.ecommerce.orders.control;

import the.chak.ecommerce.orders.control.events.ReserveStockCommand;
import the.chak.ecommerce.orders.control.events.ReleaseStockCommand;
import the.chak.ecommerce.orders.control.events.OrderCancelledEvent;
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

    @Inject
    @Channel("reserve-stock")
    Emitter<ReserveStockCommand> reserveStockEmitter;

    @Inject
    @Channel("release-stock")
    Emitter<ReleaseStockCommand> releaseStockEmitter;

    @Inject
    @Channel("order-cancelled")
    Emitter<OrderCancelledEvent> orderCancelledEmitter;

    /**
     * Publishes an {@code order-initiated} event with the given Kafka message key, parenting the
     * producer span on {@code parent} (the originating request's trace). The returned future
     * completes when the broker acks (or completes exceptionally on nack).
     */
    public CompletableFuture<Void> publishOrderInitiated(OrderDTO order, String key, Context parent) {
        LOG.infof("Publishing order-initiated event orderId=%s userId=%s", order.getId(),
                order.getUserID());
        CompletableFuture<Void> ack = new CompletableFuture<>();
        emitter.send(keyedMessage(order, key, parent, ack));
        return ack;
    }

    /**
     * A payload wrapped with its Kafka key and the trace it belongs to.
     *
     * <p>TracingMetadata.withCurrent carries the outbox-stored parent context on the message itself;
     * SmallRye's outgoing Kafka tracing reads getCurrentContext() as the producer span's parent, so
     * a relay publish stays in the request's trace even across the background-thread hop.
     */
    private static <T> Message<T> keyedMessage(
            T payload, String key, Context parent, CompletableFuture<Void> ack) {
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

    /**
     * Saga commands and the cancellation notice. All keyed by order id, so everything concerning
     * one order stays in the sequence it was produced - a release must never overtake the reserve
     * it compensates.
     */
    public CompletableFuture<Void> publishReserveStock(
            ReserveStockCommand command, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        reserveStockEmitter.send(keyedMessage(command, key, parent, ack));
        return ack;
    }

    public CompletableFuture<Void> publishReleaseStock(
            ReleaseStockCommand command, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        releaseStockEmitter.send(keyedMessage(command, key, parent, ack));
        return ack;
    }

    public CompletableFuture<Void> publishOrderCancelled(
            OrderCancelledEvent event, String key, Context parent) {
        CompletableFuture<Void> ack = new CompletableFuture<>();
        orderCancelledEmitter.send(keyedMessage(event, key, parent, ack));
        return ack;
    }
}
