package the.chak.ecommerce.orders.control;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.control.exceptions.IllegalOrderTransitionException;
import the.chak.ecommerce.orders.entity.OrderStatus;

/**
 * The order lifecycle's transition rules, in one place.
 *
 * <p>Every state change goes through here, so the legal moves are stated once rather than
 * re-derived at each call site. See docs/specs/order-lifecycle.md for the table this implements.
 *
 * <p>The table is deliberately a whitelist: a state pair absent from it is refused. That way a
 * state added to {@link OrderStatus} without a matching entry is unreachable rather than silently
 * reachable from everywhere.
 */
@ApplicationScoped
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = allowedTransitions();

    private static Map<OrderStatus, Set<OrderStatus>> allowedTransitions() {
        Map<OrderStatus, Set<OrderStatus>> allowed = new EnumMap<>(OrderStatus.class);
        allowed.put(OrderStatus.INITIATED, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.RESERVED, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.RESERVED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.REFUNDED));
        allowed.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.REFUNDED));
        // DELIVERED, CANCELLED and REFUNDED are terminal: no entry, so nothing leaves them.
        return allowed;
    }

    /** Whether an order may move from one state to another. */
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    /**
     * Checks a move and rejects it if the lifecycle does not allow it.
     *
     * @throws IllegalOrderTransitionException if the move is not legal
     */
    public void assertCanTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalOrderTransitionException(from, to);
        }
    }

    /**
     * Whether an order in this state may still be changed or removed.
     *
     * <p>Only a freshly initiated order is editable. Once it is confirmed the buyer has committed
     * and the sale has been published, so an edit would leave the operational store and the read
     * models disagreeing with no event to reconcile them. Cancelling stays available instead.
     */
    public boolean isMutable(OrderStatus status) {
        return status == OrderStatus.INITIATED;
    }
}
