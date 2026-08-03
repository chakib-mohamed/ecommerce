package the.chak.ecommerce.orders.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.control.events.StockRejectedEvent;
import the.chak.ecommerce.orders.control.events.StockReservedEvent;

/**
 * Kafka entry point for the stock step's replies. Holds no decisions of its own - whether a reply
 * is still relevant is {@link SagaService}'s judgement, and it needs the order to make it.
 */
@ApplicationScoped
public class StockReplyConsumer {

    private static final Logger LOG = Logger.getLogger(StockReplyConsumer.class);

    @Inject
    SagaService sagaService;

    @Incoming("stock-reserved")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeReserved(StockReservedEvent event) {
        LOG.infof("Stock-reserved reply received orderId=%s stepId=%s",
                event.getOrderId(), event.getStepId());
        sagaService.onStockReserved(event.getOrderId(), event.getStepId());
    }

    @Incoming("stock-rejected")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeRejected(StockRejectedEvent event) {
        LOG.infof("Stock-rejected reply received orderId=%s stepId=%s reason=%s",
                event.getOrderId(), event.getStepId(), event.getReason());
        sagaService.onStockRejected(event.getOrderId(), event.getStepId(), event.getReason());
    }
}
