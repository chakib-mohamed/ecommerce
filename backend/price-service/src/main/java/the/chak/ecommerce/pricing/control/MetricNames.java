package the.chak.ecommerce.pricing.control;

/**
 * Business (functional) metric names and tag keys for price-service. Micrometer dot-notation;
 * the Prometheus registry renders these as {@code pricing_calculations_total},
 * {@code pricing_discount_amount} (a summary, emitted as {@code _count}/{@code _sum}/buckets), and
 * {@code pricing_price_updates_total}. See {@code docs/specs/functional-metrics.md}.
 */
public final class MetricNames {

    private MetricNames() {
    }

    /** Counter - price calculations, tagged by {@link #TAG_OUTCOME}. */
    public static final String PRICING_CALCULATIONS = "pricing.calculations";

    /** Distribution summary - monetary discount applied per promoted order line. */
    public static final String PRICING_DISCOUNT_AMOUNT = "pricing.discount.amount";

    /** Counter - price updates, tagged by {@link #TAG_OUTCOME}. */
    public static final String PRICING_PRICE_UPDATES = "pricing.price.updates";

    /** Tag key distinguishing success from failure. */
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
}
