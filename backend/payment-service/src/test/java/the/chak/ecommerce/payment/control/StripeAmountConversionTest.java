package the.chak.ecommerce.payment.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turning the platform's money into the integer minor units Stripe charges in.
 *
 * <p>Small and worth its own test, because it is the last place a rounding mistake is still
 * correctable. Everything downstream of here is a real charge against a real card, so a value this
 * conversion cannot represent exactly has to stop the charge rather than be rounded into one the
 * buyer never agreed to.
 */
class StripeAmountConversionTest {

    @Test
    @DisplayName("Converts a two-decimal amount to minor units")
    void twoDecimals_convert() {
        assertEquals(1050L, StripeGatewayClient.toMinorUnits(new BigDecimal("10.50")));
    }

    @Test
    @DisplayName("Converts a whole amount to minor units")
    void wholeAmount_converts() {
        assertEquals(4000L, StripeGatewayClient.toMinorUnits(new BigDecimal("40")));
    }

    @Test
    @DisplayName("Converts an amount written with one decimal")
    void oneDecimal_converts() {
        // given - 10.5 and 10.50 are the same money written two ways; scale must not change the
        // charge
        assertEquals(1050L, StripeGatewayClient.toMinorUnits(new BigDecimal("10.5")));
    }

    @Test
    @DisplayName("Converts an amount below one unit")
    void subUnitAmount_converts() {
        assertEquals(1L, StripeGatewayClient.toMinorUnits(new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("Refuses an amount carrying more precision than money has")
    void moreThanTwoDecimals_isRefused() {
        // given - 10.555 cannot be charged exactly. Rounding it here would silently bill something
        // other than the total the buyer was shown, and the discrepancy would surface as an
        // unexplained penny in reconciliation rather than as the bug it is
        assertThrows(ArithmeticException.class,
                () -> StripeGatewayClient.toMinorUnits(new BigDecimal("10.555")));
    }

    @Test
    @DisplayName("Refuses a trailing-zero amount that is still exactly representable")
    void trailingZeros_convert() {
        // given - 10.5000 carries four decimals but loses nothing when converted; refusing it would
        // reject a perfectly chargeable amount
        assertEquals(1050L, StripeGatewayClient.toMinorUnits(new BigDecimal("10.5000")));
    }

    @Test
    @DisplayName("Refuses a null amount rather than charging zero")
    void nullAmount_isRefused() {
        // given - a zero charge would be reported as a successful payment
        assertThrows(NullPointerException.class, () -> StripeGatewayClient.toMinorUnits(null));
    }
}
