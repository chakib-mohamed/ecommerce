package the.chak.ecommerce.pricing.control;

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
    @DisplayName("Applies the percentage discount to each line and the order total")
    void applyPromotion_withPercentageOff_appliesDiscountToTotal() {
        // given
        OrderDTO order = orderWith(product("p1", 2, 100.0, 10.0));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(180.0, result.getPrice(), 0.001);
        assertEquals(90.0, result.getProducts().get(0).getPrice(), 0.001);
    }

    @Test
    @DisplayName("Charges the full price when the product has no discount")
    void applyPromotion_withoutPercentageOff_usesFullPrice() {
        // given
        OrderDTO order = orderWith(product("p1", 3, 50.0, null));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(150.0, result.getPrice(), 0.001);
        assertEquals(50.0, result.getProducts().get(0).getPrice(), 0.001);
    }

    @Test
    @DisplayName("Sums the per-line totals across multiple products into the order price")
    void applyPromotion_multipleProducts_sumsTotals() {
        // given
        ProductVO p1 = product("p1", 1, 100.0, null);
        ProductVO p2 = product("p2", 2, 50.0, 50.0);
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(p1, p2));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(150.0, result.getPrice(), 0.001);
    }

    @Test
    @DisplayName("Rounds the discounted total to two decimal places")
    void applyPromotion_roundingNeeded_returnsRoundedTwoDecimalPlaces() {
        // given
        OrderDTO order = orderWith(product("p1", 3, 10.0, 33.33));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(20.0, result.getPrice(), 0.005);
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
        p.setPrice(price);
        p.setPercentageOff(percentageOff);
        return p;
    }
}
