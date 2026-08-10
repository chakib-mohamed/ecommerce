package the.chak.ecommerce.payment.control;

/**
 * Business (functional) metric names for payment-service. Micrometer dot-notation; the Prometheus
 * registry renders these as {@code payments_captured_total} and so on. See
 * {@code docs/specs/functional-metrics.md}.
 */
public final class MetricNames {

    private MetricNames() {
    }

    /** Counter - charges the provider accepted. */
    public static final String PAYMENTS_CAPTURED = "payments.captured";

    /** Counter - charges the provider refused. A decline is an answer, and a normal one. */
    public static final String PAYMENTS_DECLINED = "payments.declined";

    /**
     * Counter - the provider did not answer, or answered something unusable.
     *
     * <p>The one to alert on. Each of these is a charge whose outcome nobody knows: the money may
     * have moved, and the saga will have given up and cancelled the order regardless. Section 8 of
     * {@code docs/specs/payment.md} calls this the worst case and says it needs an alert rather
     * than silence - reconciliation against the provider's own records is the only way back.
     */
    public static final String PAYMENTS_GATEWAY_FAULTS = "payments.gateway.faults";

    /** Counter - captures answered from the local record rather than by charging again. */
    public static final String PAYMENTS_REDELIVERED = "payments.redelivered";
}
