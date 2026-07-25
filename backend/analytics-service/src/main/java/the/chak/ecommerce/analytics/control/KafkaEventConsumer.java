package the.chak.ecommerce.analytics.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

/**
 * Feeds the warehouse from the platform's business events.
 *
 * <p>Each channel reads from the beginning of its topic, so a fresh warehouse backfills whatever
 * history the broker still holds. A message that fails every attempt lands on the channel's
 * dead-letter topic rather than blocking its partition.
 */
@ApplicationScoped
public class KafkaEventConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaEventConsumer.class);

    @Inject
    IngestionService ingestionService;

    /** Completed orders -- the only orders that reach this topic, and so the only ones counted. */
    @Incoming("order-initiated")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeOrder(OrderDTO order) {
        LOG.debugf("Received order event orderId=%s", order.getId());
        ingestionService.ingestOrder(order);
    }

    @Incoming("product-updated")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeProductUpdated(ProductUpdatedEvent event) {
        LOG.debugf("Received product update uuid=%s", event.getProduct().getUuid());
        ingestionService.upsertProduct(event.getProduct());
    }

    @Incoming("product-deleted")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeProductDeleted(ProductDeletedEvent event) {
        LOG.debugf("Received product delete uuid=%s", event.getProductUuid());
        ingestionService.markProductDeleted(event.getProductUuid().toString());
    }
}
