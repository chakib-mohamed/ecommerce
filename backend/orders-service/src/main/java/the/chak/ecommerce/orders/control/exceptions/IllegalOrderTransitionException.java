package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.orders.entity.OrderStatus;

/**
 * Raised when an order is asked to move somewhere the lifecycle does not allow - confirming an
 * order that is already confirmed, changing one that has moved past INITIATED, cancelling one that
 * has shipped. Reported as 409, because the request is well formed but the order is not in a state
 * that permits it.
 */
public class IllegalOrderTransitionException extends FunctionalException {

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(Response.Status.CONFLICT, "ILLEGAL_ORDER_TRANSITION",
                "An order cannot move from " + from + " to " + to);
    }
}
