package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.PriceChangedEvent;

@ApplicationScoped
public class PriceChangedConsumer {

    private static final Logger LOG = Logger.getLogger(PriceChangedConsumer.class);

    @Inject
    ProductService productService;

    @Incoming("price-changed")
    @Retry(maxRetries = 3, delay = 200)
    public void consume(PriceChangedEvent event) {
        LOG.infof("Price-changed event received productId=%s newPrice=%s",
                event.getProductId(), event.getNewPrice());
        productService.updatePrice(event.getProductId(), event.getNewPrice());
    }
}
