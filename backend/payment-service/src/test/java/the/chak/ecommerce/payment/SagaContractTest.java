package the.chak.ecommerce.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.bind.Jsonb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.outbox.contract.EventContracts;
import the.chak.ecommerce.payment.boundary.CustomJsonbConfigCustomizer;
import the.chak.ecommerce.payment.control.events.CapturePaymentCommand;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;

/**
 * Holds this service's copy of each payment message to the shared wire contract.
 *
 * <p>These classes exist twice - here and in orders-service - because no module may depend on
 * another service's internals. Nothing makes the two copies agree, and drift is silent.
 *
 * <p>It is worst on this pair. A renamed field on the capture command means an amount read as
 * null, a currency read as null, or a payment method read as null - and the first of those is a
 * charge for nothing while the order waits, rather than an error anybody sees. A renamed field on
 * a reply means the money was taken and the orchestrator discards the news.
 *
 * <p>orders-service runs the counterpart against the same fixtures.
 */
class SagaContractTest {

    // The service's own configuration, not a copy of it: a contract checked against a
    // hand-built config passes even when the service is configured some other way, which
    // is precisely how payment-service shipped reading and writing camelCase.
    private final Jsonb jsonb =
            EventContracts.configuredBy(new CustomJsonbConfigCustomizer()::customize);

    /** A fixture has to survive a round-trip through this service's class unchanged. */
    private void assertHonours(String topic, Class<?> type) {
        String wire = EventContracts.fixture(topic);
        Object parsed = jsonb.fromJson(wire, type);

        assertEquals(EventContracts.parse(wire), EventContracts.parse(jsonb.toJson(parsed)),
                type.getSimpleName() + " does not match the " + topic + " contract. A field this "
                        + "class does not name is dropped on the way back out, which is exactly "
                        + "what a rename looks like from here.");
    }

    @Test
    @DisplayName("Reads capture-payment in the shape the orchestrator sends")
    void capturePayment_honoursTheContract() {
        // amount and currency especially: read as null, this charges nothing and says nothing
        assertHonours("capture-payment", CapturePaymentCommand.class);
    }

    @Test
    @DisplayName("Sends payment-captured in the shape the orchestrator reads")
    void paymentCaptured_honoursTheContract() {
        assertHonours("payment-captured", PaymentCapturedEvent.class);
    }

    @Test
    @DisplayName("Sends payment-failed in the shape the orchestrator reads")
    void paymentFailed_honoursTheContract() {
        assertHonours("payment-failed", PaymentFailedEvent.class);
    }
}
