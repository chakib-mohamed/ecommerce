package the.chak.ecommerce.products.control;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

@ApplicationScoped
public class KafkaEventConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaEventConsumer.class);

    @Inject
    ProductService productService;

    @Incoming("product-updated")
    public void consumeProductUpdated(ProductUpdatedEvent event) {
        LOG.infof("Product-updated event received productId=%s", event.getProduct().getUuid());
        productService.onProductUpdated(event);
    }

    @Incoming("product-deleted")
    public void consumeProductDeleted(ProductDeletedEvent event) {
        LOG.infof("Product-deleted event received productId=%s", event.getProductUuid());
        productService.onProductDeleted(event);
    }
}
