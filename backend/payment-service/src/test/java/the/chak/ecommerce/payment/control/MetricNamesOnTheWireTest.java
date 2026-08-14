package the.chak.ecommerce.payment.control;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The names these counters are published under, as the alert rules spell them.
 *
 * <p>Micrometer takes a dotted name and Prometheus renders a different string - underscores, and a
 * {@code _total} suffix on counters. The alert rules in {@code observability/rules} are written
 * against the rendered form, and nothing else connects the two.
 *
 * <p>That gap fails silently and in the worst direction. An alert whose metric does not exist never
 * fires, and a rule that never fires is indistinguishable from a system that is fine. These
 * assertions are the only thing standing between a renamed counter and an alert everyone believes
 * is watching something.
 */
class MetricNamesOnTheWireTest {

    private String scrape(String meterName) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter(meterName).increment();
        return registry.scrape();
    }

    private void assertPublishedAs(String meterName, String promName) {
        String scraped = scrape(meterName);
        assertTrue(scraped.contains(promName),
                meterName + " is not published as " + promName + ", which is the name the alert "
                        + "rules use. An alert on a metric that does not exist never fires.\n"
                        + scraped);
    }

    @Test
    @DisplayName("Publishes gateway faults as payments_gateway_faults_total")
    void gatewayFaults_matchTheAlertRule() {
        // the PaymentGatewayFault alert is the one guarding money taken against a cancelled order
        assertPublishedAs(MetricNames.PAYMENTS_GATEWAY_FAULTS, "payments_gateway_faults_total");
    }

    @Test
    @DisplayName("Publishes captures as payments_captured_total")
    void captured_matchesTheAlertRule() {
        assertPublishedAs(MetricNames.PAYMENTS_CAPTURED, "payments_captured_total");
    }

    @Test
    @DisplayName("Publishes declines as payments_declined_total")
    void declined_matchesTheAlertRule() {
        assertPublishedAs(MetricNames.PAYMENTS_DECLINED, "payments_declined_total");
    }

    @Test
    @DisplayName("Publishes redeliveries as payments_redelivered_total")
    void redelivered_matchesTheAlertRule() {
        assertPublishedAs(MetricNames.PAYMENTS_REDELIVERED, "payments_redelivered_total");
    }
}
