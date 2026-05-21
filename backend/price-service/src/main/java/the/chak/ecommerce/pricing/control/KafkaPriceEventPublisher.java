package the.chak.ecommerce.pricing.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;

@ApplicationScoped
public class KafkaPriceEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaPriceEventPublisher.class);

    @Inject
    @Channel("price-changed")
    Emitter<PriceChangedEvent> emitter;

    public void publish(PriceChangedEvent event) {
        try {
            emitter.send(event).toCompletableFuture().get();
        } catch (Exception e) {
            LOG.error("Failed to publish price-changed event for productId=" + event.getProductId(), e);
            throw new RuntimeException("Failed to publish price event", e);
        }
    }
}
