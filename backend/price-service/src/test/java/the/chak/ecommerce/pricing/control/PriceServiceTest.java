package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;

/**
 * Unit-tests the price validation guard. The persistence + publish behavior now runs inside a Mongo
 * transaction (price doc + outbox entry written atomically), so it is exercised against real
 * Testcontainers in {@code PriceOutboxWritePathTest} rather than mocked here.
 */
@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @InjectMocks
    PriceService priceService;

    // A real registry so price-update outcome counters are recorded and assertable.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // -- update validation --------

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a null price")
    void update_nullPrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", null));
    }

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a zero price")
    void update_zeroPrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", 0.0));
    }

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a negative price")
    void update_negativePrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", -5.0));
    }

    // --metrics ------------------------------------------------------------

    @Test
    @DisplayName("Counts a failure outcome when the price is invalid")
    void update_invalidPrice_recordsFailureOutcome() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", null));
        assertEquals(1.0,
                meterRegistry.get("pricing.price.updates").tag("outcome", "failure").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Records no success outcome when the price is invalid")
    void update_invalidPrice_recordsNoSuccessOutcome() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", 0.0));
        assertNull(meterRegistry.find("pricing.price.updates").tag("outcome", "success").counter());
    }
}
