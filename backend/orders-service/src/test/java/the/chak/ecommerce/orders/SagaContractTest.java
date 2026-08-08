package the.chak.ecommerce.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.bind.Jsonb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.CustomJsonbConfigCustomizer;
import the.chak.ecommerce.orders.control.events.CapturePaymentCommand;
import the.chak.ecommerce.orders.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.orders.control.events.PaymentFailedEvent;
import the.chak.ecommerce.orders.control.events.ReleaseStockCommand;
import the.chak.ecommerce.orders.control.events.ReserveStockCommand;
import the.chak.ecommerce.orders.control.events.StockRejectedEvent;
import the.chak.ecommerce.orders.control.events.StockReservedEvent;
import the.chak.ecommerce.outbox.contract.EventContracts;

/**
 * Holds this service's copy of each saga message to the shared wire contract.
 *
 * <p>Every one of these classes exists twice - here and in the service at the other end - because
 * no module may depend on another service's internals. Nothing makes the two copies agree, and
 * drift is silent: rename a field on one side and the code compiles, the message publishes, and
 * JSON-B leaves the field null at the other end. A reply carrying a null step id is discarded by
 * the orchestrator's own guard, so the symptom is not an error but every saga stalling to its
 * deadline and every order cancelled for a timeout that never happened.
 *
 * <p>The counterpart tests are {@code products-service}'s and {@code payment-service}'s, reading
 * the same fixtures. A field renamed on either side fails on that side, which is where the fix
 * belongs.
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

    // -- commands this service sends ----------------------------------------

    @Test
    @DisplayName("Sends reserve-stock in the shape the catalog reads")
    void reserveStock_honoursTheContract() {
        assertHonours("reserve-stock", ReserveStockCommand.class);
    }

    @Test
    @DisplayName("Sends release-stock in the shape the catalog reads")
    void releaseStock_honoursTheContract() {
        assertHonours("release-stock", ReleaseStockCommand.class);
    }

    @Test
    @DisplayName("Sends capture-payment in the shape the payment service reads")
    void capturePayment_honoursTheContract() {
        assertHonours("capture-payment", CapturePaymentCommand.class);
    }

    // -- replies this service reads ------------------------------------------

    @Test
    @DisplayName("Reads stock-reserved in the shape the catalog sends")
    void stockReserved_honoursTheContract() {
        assertHonours("stock-reserved", StockReservedEvent.class);
    }

    @Test
    @DisplayName("Reads stock-rejected in the shape the catalog sends")
    void stockRejected_honoursTheContract() {
        assertHonours("stock-rejected", StockRejectedEvent.class);
    }

    @Test
    @DisplayName("Reads payment-captured in the shape the payment service sends")
    void paymentCaptured_honoursTheContract() {
        assertHonours("payment-captured", PaymentCapturedEvent.class);
    }

    @Test
    @DisplayName("Reads payment-failed in the shape the payment service sends")
    void paymentFailed_honoursTheContract() {
        assertHonours("payment-failed", PaymentFailedEvent.class);
    }
}
