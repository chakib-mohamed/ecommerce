package the.chak.ecommerce.products.control;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

@ApplicationScoped
public class KafkaEventConsumer {

    @Inject
    ProductService productService;

    @Incoming("product-updated")
    public void consumeProductUpdated(ProductUpdatedEvent event) {
        productService.onProductUpdated(event);
    }

    @Incoming("product-deleted")
    public void consumeProductDeleted(ProductDeletedEvent event) {
        productService.onProductDeleted(event);
    }
}
