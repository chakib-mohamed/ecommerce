package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import the.chak.ecommerce.products.control.events.PriceChangedEvent;

@ApplicationScoped
public class PriceChangedConsumer {

    @Inject
    ProductService productService;

    @Incoming("price-changed")
    public void consume(PriceChangedEvent event) {
        productService.updatePrice(event.getProductId(), event.getNewPrice());
    }
}
