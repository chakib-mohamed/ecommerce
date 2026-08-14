package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.Money;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationResponse;

/**
 * What amounts are, rather than what they come to: that a total is rounded once instead of twice,
 * that the result is exact at cent scale, and that it says what currency it is in.
 *
 * <p>The old path rounded in two places - the promotions service rounded a total that the pricing
 * service then recomputed and rounded again from unrounded unit prices. Half of that work was
 * discarded, and the half that survived started from figures that had never been rounded at all.
 */
@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PricingMoneyTest {

    @Inject
    PricingService pricingService;

    @Test
    @DisplayName("Rounds each discounted unit price once and sums those, exactly")
    void discountedLine_isRoundedAtTheLineNotTheTotal() {
        // given - 33.33% off 10.00 is 6.667 a unit, which has no exact cent value
        PriceCalculationResponse response = pricingService.calculate(
                requestWith(product("p1", 3, "10.00", 33.33)));

        // then - the unit rounds half-up to 6.67 and three of them is 20.01 exactly.
        // Rounding the raw 20.001 instead would have said 20.00, and the buyer would be
        // charged a cent less than the sum of the prices they were shown.
        assertEquals(0, new BigDecimal("20.01").compareTo(response.getOrder().getPrice()),
                "expected 20.01, was " + response.getOrder().getPrice());
    }

    @Test
    @DisplayName("Returns a total carrying exactly two decimal places")
    void total_isAtCentScale() {
        PriceCalculationResponse response = pricingService.calculate(
                requestWith(product("p1", 3, "10.00", null)));

        assertEquals(Money.SCALE, response.getOrder().getPrice().scale(),
                "a published amount must be at cent scale, was "
                        + response.getOrder().getPrice());
    }

    @Test
    @DisplayName("Denominates the priced order in the platform currency")
    void total_carriesTheCurrency() {
        PriceCalculationResponse response = pricingService.calculate(
                requestWith(product("p1", 1, "10.00", null)));

        assertEquals(Money.DEFAULT_CURRENCY, response.getOrder().getCurrency());
    }

    @Test
    @DisplayName("Keeps a price exact where a double would drift")
    void repeatedAddition_doesNotDrift() {
        // given - 0.10 has no exact binary representation, so ten of them summed as doubles
        // comes to 0.9999999999999999. As decimals it is exactly 1.00.
        PriceCalculationResponse response = pricingService.calculate(
                requestWith(product("p1", 10, "0.10", null)));

        assertEquals(0, new BigDecimal("1.00").compareTo(response.getOrder().getPrice()),
                "expected exactly 1.00, was " + response.getOrder().getPrice());
    }

    // -- helpers ------------------------------------------------------------

    private static PriceCalculationRequest requestWith(ProductVO product) {
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(product));
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);
        return request;
    }

    private static ProductVO product(String id, int qty, String price, Double percentageOff) {
        ProductVO p = new ProductVO();
        p.setProductID(id);
        p.setQty(qty);
        // built from a string: new BigDecimal(10.0) would carry the binary approximation in
        p.setPrice(new BigDecimal(price));
        p.setPercentageOff(percentageOff);
        return p;
    }
}
