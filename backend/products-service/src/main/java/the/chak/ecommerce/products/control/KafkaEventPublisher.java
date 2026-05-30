package the.chak.ecommerce.products.control;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

@ApplicationScoped
public class KafkaEventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    @Inject
    @Channel("product-updated")
    Emitter<ProductUpdatedEvent> productUpdatedEmitter;


    @Inject
    @Channel("product-deleted")
    Emitter<ProductDeletedEvent> productDeletedEmitter;

    public void onProductUpdated(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ProductUpdatedEvent event) {
        LOG.infof("Publishing product-updated event productId=%s", event.getProduct().getUuid());
        productUpdatedEmitter.send(event);
    }

    public void onProductDeleted(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ProductDeletedEvent event) {
        LOG.infof("Publishing product-deleted event productId=%s", event.getProductUuid());
        productDeletedEmitter.send(event);
    }
}
