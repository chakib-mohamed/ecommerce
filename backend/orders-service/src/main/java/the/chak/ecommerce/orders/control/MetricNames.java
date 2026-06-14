package the.chak.ecommerce.orders.control;

/**
 * Business (functional) metric names and tag keys for orders-service. Micrometer dot-notation;
 * the Prometheus registry renders these as {@code orders_created_total}, {@code order_value_amount},
 * {@code orders_confirmed_total}, and {@code checkouts_total}. See
 * {@code docs/specs/functional-metrics.md}.
 */
public final class MetricNames {

    private MetricNames() {
    }

    /** Counter - orders successfully created. */
    public static final String ORDERS_CREATED = "orders.created";

    /** Distribution summary - monetary value of each created order. */
    public static final String ORDER_VALUE = "order.value.amount";

    /** Counter - orders confirmed. */
    public static final String ORDERS_CONFIRMED = "orders.confirmed";

    /** Counter - cart checkouts, tagged by {@link #TAG_OUTCOME}. */
    public static final String CHECKOUTS = "checkouts";

    /** Tag key distinguishing success from failure. */
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
}
