package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * Raised when an order changed between being read and being written - two confirmations racing,
 * or a delete landing in between. Reported as 409, and the caller can safely re-read and retry.
 */
public class ConcurrentOrderModificationException extends FunctionalException {

    public ConcurrentOrderModificationException(String orderId) {
        super(Response.Status.CONFLICT, "ORDER_MODIFIED_CONCURRENTLY",
                "Order " + orderId + " changed while it was being confirmed; read it again and retry");
    }
}
