package the.chak.ecommerce.authentication.control;

/**
 * Business (functional) metric names and tag keys for authenticate-service. Micrometer
 * dot-notation; the Prometheus registry renders these as {@code auth_logins_total} and
 * {@code auth_registrations_total}. See {@code docs/specs/functional-metrics.md}.
 */
public final class MetricNames {

    private MetricNames() {
    }

    /** Counter - login attempts, tagged by {@link #TAG_OUTCOME}. */
    public static final String AUTH_LOGINS = "auth.logins";

    /** Counter - user registrations, tagged by {@link #TAG_OUTCOME}. */
    public static final String AUTH_REGISTRATIONS = "auth.registrations";

    /** Tag key distinguishing success from failure. */
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
}
