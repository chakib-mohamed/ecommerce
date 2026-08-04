package the.chak.ecommerce.payment.control;

/**
 * The provider did not answer, or answered something we cannot interpret.
 *
 * <p>Distinct from a decline on purpose. A decline is a known outcome and is recorded; this is the
 * case where nobody knows whether money moved, so nothing may be recorded and the command must be
 * left to redeliver. The idempotency key is what makes that retry safe.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentGatewayException(String message) {
        super(message);
    }
}
