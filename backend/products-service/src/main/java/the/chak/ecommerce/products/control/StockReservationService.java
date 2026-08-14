package the.chak.ecommerce.products.control;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.entity.ReservationStatus;
import the.chak.ecommerce.products.entity.StockReservation;
import the.chak.ecommerce.products.repository.ProductRepository;
import the.chak.ecommerce.products.repository.StockReservationRepository;

/**
 * Holds catalog stock against an order and gives it back again.
 *
 * <p>Nothing read {@code stock} before this existed, so the catalog would sell the same unit to
 * every buyer who asked. Reservation is what makes a unit unavailable once someone is buying it.
 *
 * <p>Both operations are keyed by order and are idempotent, because saga commands are delivered
 * at-least-once: a repeat is a no-op reporting the original outcome, never a second decrement.
 * Reservations carry no expiry (see section 10.3 of {@code docs/specs/order-lifecycle.md}), so the
 * only thing that ever returns held stock is an explicit {@link #release}.
 */
@ApplicationScoped
public class StockReservationService {

    private static final Logger LOG = Logger.getLogger(StockReservationService.class);

    @Inject
    ProductRepository productRepository;

    @Inject
    StockReservationRepository reservationRepository;

    /**
     * Holds every line of an order, or none of them.
     *
     * <p>All-or-nothing because the lifecycle has no state for a partly filled order: either the
     * order can be met and moves to RESERVED, or it cannot and is cancelled.
     *
     * @return true when the stock is held
     */
    @Transactional
    public boolean reserve(String orderId, List<StockLine> lines) {
        List<StockReservation> existing = reservationRepository.findByOrderId(orderId);
        if (!existing.isEmpty()) {
            // Already decided. Re-testing would consult stock that has since moved and could
            // contradict the reply the orchestrator already acted on.
            boolean held = existing.stream().anyMatch(r -> r.getStatus() != ReservationStatus.REJECTED);
            LOG.infof("Reserve redelivered orderId=%s outcome=%s", orderId, held ? "held" : "rejected");
            return held;
        }

        List<StockLine> taken = new java.util.ArrayList<>(lines.size());
        for (StockLine line : lines) {
            if (!productRepository.decrementStockIfAvailable(
                    UUID.fromString(line.productId()), line.quantity())) {
                // Undo by hand rather than by rolling back: the transaction has to commit so the
                // REJECTED rows survive, and a rollback would take those with it. Only the lines
                // already taken are given back.
                taken.forEach(t -> productRepository.incrementStock(
                        UUID.fromString(t.productId()), t.quantity()));
                lines.forEach(l -> record(orderId, l, ReservationStatus.REJECTED));
                LOG.infof("Reservation rejected orderId=%s productId=%s wanted=%d restored=%d",
                        orderId, line.productId(), line.quantity(), taken.size());
                return false;
            }
            taken.add(line);
        }

        lines.forEach(line -> record(orderId, line, ReservationStatus.HELD));
        LOG.infof("Stock reserved orderId=%s lines=%d", orderId, lines.size());
        return true;
    }

    /**
     * Gives back whatever this order is holding.
     *
     * <p>An unknown order is not an error: releasing after a rejected reservation is a legitimate
     * saga path, and so is a redelivered compensation.
     */
    @Transactional
    public void release(String orderId) {
        List<StockReservation> held =
                reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.HELD);
        if (held.isEmpty()) {
            LOG.debugf("Release for orderId=%s holds nothing - nothing to give back", orderId);
            return;
        }

        held.forEach(reservation -> {
            productRepository.incrementStock(
                    UUID.fromString(reservation.getProductId()), reservation.getQuantity());
            reservation.setStatus(ReservationStatus.RELEASED);
        });
        LOG.infof("Stock released orderId=%s lines=%d", orderId, held.size());
    }

    private void record(String orderId, StockLine line, ReservationStatus status) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setProductId(line.productId());
        reservation.setQuantity(line.quantity());
        reservation.setStatus(status);
        reservation.setCreatedAt(LocalDateTime.now());
        reservationRepository.persist(reservation);
    }
}
