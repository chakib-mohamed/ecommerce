package the.chak.ecommerce.products.control;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.ReleaseStockCommand;
import the.chak.ecommerce.products.control.events.ReserveStockCommand;
import the.chak.ecommerce.products.control.events.StockRejectedEvent;
import the.chak.ecommerce.products.control.events.StockReservedEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * The saga's stock step, seen from the catalog's side: take the stock and say so, or refuse and say
 * that instead.
 *
 * <p>The reply is written to the outbox in the <b>same transaction</b> as the stock change. Sending
 * it directly to Kafka would be a dual write - the stock could commit while the reply was lost, and
 * the order would sit waiting for an answer that had already happened. That is the whole reason
 * ADR-0002 exists.
 *
 * <p>Replies are keyed by order id so the broker keeps one order's replies in order, and they carry
 * back the {@code stepId} they were asked with so the orchestrator can tell a current reply from one
 * belonging to a step it has already moved past.
 */
@ApplicationScoped
public class StockCommandHandler {

    private static final Logger LOG = Logger.getLogger(StockCommandHandler.class);

    /** Told to the orchestrator, and through it to the buyer, when the catalog cannot deliver. */
    private static final String REASON_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    @Inject
    StockReservationService reservationService;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxRelay outboxRelay;

    /**
     * Holds stock for an order and replies with the outcome.
     *
     * <p>Safe to redeliver: {@link StockReservationService#reserve} reports the original outcome
     * rather than deciding again, so a repeat produces the same reply instead of taking the stock
     * twice.
     */
    @Transactional
    public void handleReserve(ReserveStockCommand command) {
        List<StockLine> lines = command.getLines().stream()
                .map(line -> new StockLine(line.getProductId(), line.getQuantity()))
                .toList();

        boolean held = reservationService.reserve(command.getOrderId(), lines);

        if (held) {
            outboxRepository.persist(outboxEventFactory.stockReserved(command.getOrderId(),
                    new StockReservedEvent(command.getOrderId(), command.getStepId())));
        } else {
            outboxRepository.persist(outboxEventFactory.stockRejected(command.getOrderId(),
                    new StockRejectedEvent(command.getOrderId(), command.getStepId(),
                            REASON_INSUFFICIENT_STOCK)));
        }

        LOG.infof("Reserve-stock handled orderId=%s stepId=%s outcome=%s",
                command.getOrderId(), command.getStepId(), held ? "reserved" : "rejected");
        outboxRelay.requestPoll();
    }

    /**
     * Gives back whatever the order is holding.
     *
     * <p>Sends no reply. A compensation has no failure the orchestrator could act on: the order is
     * already on its way to CANCELLED, and there is no state for "failed to release". Repeat
     * delivery is a no-op.
     */
    @Transactional
    public void handleRelease(ReleaseStockCommand command) {
        reservationService.release(command.getOrderId());
        LOG.infof("Release-stock handled orderId=%s stepId=%s",
                command.getOrderId(), command.getStepId());
    }
}
