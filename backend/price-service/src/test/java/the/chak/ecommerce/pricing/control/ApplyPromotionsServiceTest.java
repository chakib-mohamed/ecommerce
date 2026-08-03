package the.chak.ecommerce.pricing.control;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;

class ApplyPromotionsServiceTest {

    ApplyPromotionsService applyPromotionsService;

    // A real registry so the discount-amount summary is recorded and assertable.
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        applyPromotionsService = new ApplyPromotionsService();
        applyPromotionsService.meterRegistry = meterRegistry;
    }

    @Test
    @DisplayName("Applies the percentage discount to the line's unit price")
    void applyPromotion_withPercentageOff_discountsTheUnitPrice() {
        // given
        OrderDTO order = orderWith(product("p1", 2, 100.0, 10.0));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(0, new BigDecimal("90.00").compareTo(result.getProducts().get(0).getPrice()),
                "expected 90.00, was " + result.getProducts().get(0).getPrice());
        // The total is deliberately left unset here: Drools runs next and may move unit prices
        // again, so PricingService computes it once, afterwards.
        assertNull(result.getPrice(), "applyPromotion must not set the order total");
    }

    @Test
    @DisplayName("Charges the full price when the product has no discount")
    void applyPromotion_withoutPercentageOff_usesFullPrice() {
        // given
        OrderDTO order = orderWith(product("p1", 3, 50.0, null));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getProducts().get(0).getPrice()),
                "an undiscounted line keeps its price, was "
                        + result.getProducts().get(0).getPrice());
    }

    @Test
    @DisplayName("Discounts each line independently when products are mixed")
    void applyPromotion_multipleProducts_discountsEachLine() {
        // given
        ProductVO p1 = product("p1", 1, 100.0, null);
        ProductVO p2 = product("p2", 2, 50.0, 50.0);
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(p1, p2));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getProducts().get(0).getPrice()),
                "undiscounted line, was " + result.getProducts().get(0).getPrice());
        assertEquals(0, new BigDecimal("25.00").compareTo(result.getProducts().get(1).getPrice()),
                "50% off 50.00, was " + result.getProducts().get(1).getPrice());
    }

    @Test
    @DisplayName("Rounds a discounted unit price to two decimal places, once")
    void applyPromotion_roundingNeeded_returnsRoundedTwoDecimalPlaces() {
        // given
        // 33.33% off 10.00 is 6.667, which has no exact cent value
        OrderDTO order = orderWith(product("p1", 3, 10.0, 33.33));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(0, new BigDecimal("6.67").compareTo(result.getProducts().get(0).getPrice()),
                "expected 6.67 (half-up), was " + result.getProducts().get(0).getPrice());
    }

    // --metrics ------------------------------------------------------------

    @Test
    @DisplayName("Records the monetary discount amount for a discounted line")
    void applyPromotion_withPercentageOff_recordsDiscountAmount() {
        // given - 10% off 100.0 over qty 2 -> discount = 100.0 * 0.10 * 2 = 20.0
        OrderDTO order = orderWith(product("p1", 2, 100.0, 10.0));

        // when
        applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(1L, meterRegistry.get("pricing.discount.amount").summary().count());
        assertEquals(20.0, meterRegistry.get("pricing.discount.amount").summary().totalAmount(), 0.001);
    }

    @Test
    @DisplayName("Records no discount amount when the line has no promotion")
    void applyPromotion_withoutPercentageOff_recordsNoDiscount() {
        // given
        OrderDTO order = orderWith(product("p1", 3, 50.0, null));

        // when
        applyPromotionsService.applyPromotion(order);

        // then
        assertNull(meterRegistry.find("pricing.discount.amount").summary());
    }

    @Test
    @DisplayName("Records a discount only for the discounted line when products are mixed")
    void applyPromotion_multipleProducts_recordsOnlyDiscountedLine() {
        // given - p1 has no promotion; p2 = 50% off 50.0 over qty 2 -> discount = 50.0
        ProductVO p1 = product("p1", 1, 100.0, null);
        ProductVO p2 = product("p2", 2, 50.0, 50.0);
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(p1, p2));

        // when
        applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(1L, meterRegistry.get("pricing.discount.amount").summary().count());
        assertEquals(50.0, meterRegistry.get("pricing.discount.amount").summary().totalAmount(), 0.001);
    }

    // -- helpers ------------------------------------------------------------

    private static OrderDTO orderWith(ProductVO product) {
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(product));
        return order;
    }

    private static ProductVO product(String id, int qty, double price, Double percentageOff) {
        ProductVO p = new ProductVO();
        p.setProductID(id);
        p.setQty(qty);
        p.setPrice(BigDecimal.valueOf(price));
        p.setPercentageOff(percentageOff);
        return p;
    }
}
