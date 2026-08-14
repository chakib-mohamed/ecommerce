package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The names these counters are published under, as the alert rules spell them.
 *
 * <p>Micrometer takes a dotted name and Prometheus renders a different string - underscores, and a
 * {@code _total} suffix on counters. {@code orders.sagas.timed.out} in particular becomes
 * {@code orders_sagas_timed_out_total}, which is not obvious and is written by hand in
 * {@code observability/rules/order-lifecycle.rules.yml}.
 *
 * <p>The failure this prevents is silent and points the wrong way: an alert whose metric does not
 * exist never fires, and a rule that never fires looks exactly like a system with nothing wrong.
 * Only the metrics the alert rules actually reference are pinned here - the rest are dashboard
 * material, and a dashboard with a missing panel is at least visibly missing.
 */
class MetricNamesOnTheWireTest {

    private void assertPublishedAs(String meterName, String promName) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter(meterName).increment();
        String scraped = registry.scrape();

        assertTrue(scraped.contains(promName),
                meterName + " is not published as " + promName + ", which is the name the alert "
                        + "rules use. An alert on a metric that does not exist never fires.\n"
                        + scraped);
    }

    @Test
    @DisplayName("Publishes abandoned sagas as orders_sagas_timed_out_total")
    void sagasTimedOut_matchesTheAlertRule() {
        // the SagaStepTimedOut alert; every one of these held stock out of sale
        assertPublishedAs(MetricNames.SAGAS_TIMED_OUT, "orders_sagas_timed_out_total");
    }

    @Test
    @DisplayName("Publishes paid orders as orders_paid_total")
    void ordersPaid_matchesTheAlertRule() {
        // half of OrdersConfirmedButNonePaid; if this name is wrong the alert fires constantly
        // rather than never, which is its own kind of useless
        assertPublishedAs(MetricNames.ORDERS_PAID, "orders_paid_total");
    }

    @Test
    @DisplayName("Publishes confirmed orders as orders_confirmed_total")
    void ordersConfirmed_matchesTheAlertRule() {
        assertPublishedAs(MetricNames.ORDERS_CONFIRMED, "orders_confirmed_total");
    }
}
