package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
}
