package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.ReleaseStockCommand;
import the.chak.ecommerce.products.control.events.ReserveStockCommand;

/**
 * Kafka entry point for the saga's stock commands. Holds no logic of its own - the work and the
 * reply are one transaction in {@link StockCommandHandler}, which this must not split.
 */
@ApplicationScoped
public class StockCommandConsumer {

    private static final Logger LOG = Logger.getLogger(StockCommandConsumer.class);

    @Inject
    StockCommandHandler handler;

    @Incoming("reserve-stock")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeReserve(ReserveStockCommand command) {
        LOG.infof("Reserve-stock command received orderId=%s stepId=%s lines=%d",
                command.getOrderId(), command.getStepId(),
                command.getLines() == null ? 0 : command.getLines().size());
        handler.handleReserve(command);
    }

    @Incoming("release-stock")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeRelease(ReleaseStockCommand command) {
        LOG.infof("Release-stock command received orderId=%s stepId=%s",
                command.getOrderId(), command.getStepId());
        handler.handleRelease(command);
    }
}
