package the.chak.ecommerce.payment.control;

/**
 * What the provider said about a charge.
 *
 * <p>A decline is a result, not an exception: the provider answered, and the answer was no. Only a
 * provider that failed to answer at all is an error, because that is the case where nobody knows
 * whether money moved.
 *
 * @param captured whether the money was taken
 * @param providerRef the provider's identifier for the charge; null when it was refused
 * @param failureReason why it was refused; null when it was taken
 */
public record ChargeResult(boolean captured, String providerRef, String failureReason) {

    public static ChargeResult captured(String providerRef) {
        return new ChargeResult(true, providerRef, null);
    }

    public static ChargeResult declined(String failureReason) {
        return new ChargeResult(false, null, failureReason);
    }
}
