package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.bind.Jsonb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.outbox.contract.EventContracts;
import the.chak.ecommerce.products.boundary.CustomJsonbConfigCustomizer;
import the.chak.ecommerce.products.control.events.ReleaseStockCommand;
import the.chak.ecommerce.products.control.events.ReserveStockCommand;
import the.chak.ecommerce.products.control.events.StockRejectedEvent;
import the.chak.ecommerce.products.control.events.StockReservedEvent;

/**
 * Holds the catalog's copy of each stock message to the shared wire contract.
 *
 * <p>These four classes exist twice - here and in orders-service - because no module may depend on
 * another service's internals. Nothing makes the two copies agree, and drift is silent: rename a
 * field here and the code compiles, the message still arrives, and JSON-B leaves the field null.
 * A reserve command whose step id is null produces a reply the orchestrator's guard discards, so
 * the stock is taken and the order stalls to its deadline anyway - the worst combination available.
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
    @DisplayName("Reads reserve-stock in the shape the orchestrator sends")
    void reserveStock_honoursTheContract() {
        // the nested lines matter as much as the envelope: a renamed quantity reserves nothing
        assertHonours("reserve-stock", ReserveStockCommand.class);
    }

    @Test
    @DisplayName("Reads release-stock in the shape the orchestrator sends")
    void releaseStock_honoursTheContract() {
        assertHonours("release-stock", ReleaseStockCommand.class);
    }

    @Test
    @DisplayName("Sends stock-reserved in the shape the orchestrator reads")
    void stockReserved_honoursTheContract() {
        assertHonours("stock-reserved", StockReservedEvent.class);
    }

    @Test
    @DisplayName("Sends stock-rejected in the shape the orchestrator reads")
    void stockRejected_honoursTheContract() {
        assertHonours("stock-rejected", StockRejectedEvent.class);
    }
}
