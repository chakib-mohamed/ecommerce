package the.chak.ecommerce.pricing.control;

import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;

/**
 * Sole owner of the {@code price-changed} outgoing channel (SmallRye allows one emitter per
 * channel). The keyed {@code publishPriceChanged} method is what {@link OutboxRelay} uses to drain
 * the outbox with a Kafka message key.
 */
@ApplicationScoped
public class KafkaPriceEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaPriceEventPublisher.class);

    @Inject
    @Channel("price-changed")
    Emitter<PriceChangedEvent> emitter;

    /**
     * Publishes a {@code price-changed} event with the given Kafka message key. The returned future
     * completes when the broker acks (or completes exceptionally on nack).
     */
    public CompletableFuture<Void> publishPriceChanged(PriceChangedEvent event, String key) {
        LOG.infof("Publishing price-changed event productId=%s newPrice=%.2f",
                event.getProductId(), event.getNewPrice());
        CompletableFuture<Void> ack = new CompletableFuture<>();
        Metadata metadata = Metadata.of(
                OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build());
        emitter.send(Message.of(event, metadata,
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
