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

    /** Counter - orders cancelled, whether before or after confirmation. */
    public static final String ORDERS_CANCELLED = "orders.cancelled";

    /** Orders whose stock the catalog has confirmed it is holding. */
    public static final String ORDERS_RESERVED = "orders.reserved";

    /** Counter - orders whose payment has been taken. This, not confirmation, is revenue. */
    public static final String ORDERS_PAID = "orders.paid";

    /** Sagas abandoned because a step ran past its deadline. Alert on this: each one held stock. */
    /**
     * Orders cancelled for sitting uncommitted past their TTL. Separate from orders.cancelled on
     * purpose: a buyer changing their mind and a quote nobody ever acted on are different facts,
     * and a rise in this one says something about checkout rather than about demand.
     */
    public static final String ORDERS_EXPIRED = "orders.expired";

    public static final String SAGAS_TIMED_OUT = "orders.sagas.timed.out";

    /** Counter - cart checkouts, tagged by {@link #TAG_OUTCOME}. */
    public static final String CHECKOUTS = "checkouts";

    /** Tag key distinguishing success from failure. */
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
}
