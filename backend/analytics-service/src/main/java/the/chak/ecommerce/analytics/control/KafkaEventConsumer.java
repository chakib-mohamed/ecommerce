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

    /**
     * Paid orders -- money actually taken, and so the only thing counted as revenue.
     *
     * <p>Deliberately not a confirmation: counting one would report an order whose card was later
     * declined as revenue. An order is either paid or cancelled and never both, so nothing has to
     * arrive later to take a counted sale back.
     */
    @Incoming("order-paid")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeOrder(OrderDTO order) {
        LOG.debugf("Received paid order event orderId=%s", order.getId());
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
