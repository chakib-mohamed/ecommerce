package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * Raised when an order is confirmed without a payment method reference.
 *
 * <p>Refused up front rather than at the charge: confirming reserves stock first, so an order that
 * could never have been paid for would take inventory out of sale and only fail once the capture
 * found nothing to charge against.
 */
public class MissingPaymentMethodException extends FunctionalException {

    public MissingPaymentMethodException() {
        super(Response.Status.BAD_REQUEST, "MISSING_PAYMENT_METHOD",
                "A payment method is required to confirm an order");
    }
}
