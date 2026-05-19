package the.chak.ecommerce.products.control;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

@ApplicationScoped
public class KafkaEventPublisher {

    @Inject
    @Channel("product-updated")
    Emitter<ProductUpdatedEvent> productUpdatedEmitter;


    @Inject
    @Channel("product-deleted")
    Emitter<ProductDeletedEvent> productDeletedEmitter;

    public void onProductUpdated(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ProductUpdatedEvent event) {
        productUpdatedEmitter.send(event);
    }

    public void onProductDeleted(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ProductDeletedEvent event) {
        productDeletedEmitter.send(event);
    }
}
