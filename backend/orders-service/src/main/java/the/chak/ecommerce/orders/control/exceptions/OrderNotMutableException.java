package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.orders.entity.OrderStatus;

/**
 * Raised when an order is edited or removed after it has been confirmed. Reported as 409: the
 * request is well formed, but the order has moved past the point where it can still be changed.
 *
 * <p>Distinct from {@link IllegalOrderTransitionException}, which covers a bad lifecycle move
 * rather than an edit to a frozen order.
 */
public class OrderNotMutableException extends FunctionalException {

    public OrderNotMutableException(OrderStatus status) {
        super(Response.Status.CONFLICT, "ORDER_NOT_MUTABLE",
                "An order that is " + status + " can no longer be changed; cancel it instead");
    }
}
