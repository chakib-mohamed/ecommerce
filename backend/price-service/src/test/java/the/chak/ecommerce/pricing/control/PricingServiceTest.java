package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationResponse;
import the.chak.ecommerce.pricing.control.exceptions.InvalidOrderException;

class PricingServiceTest {

    PricingService pricingService;

    @BeforeEach
    void setUp() {
        // ApplyPromotionsService is pure logic; Drools runs in-process from a classpath rule
        // file — neither needs a container, so no @QuarkusTest is required.
        pricingService = new PricingService();
        pricingService.applyPromotionsService = new ApplyPromotionsService();
        pricingService.init();
    }

    @Test
    @DisplayName("Throws InvalidOrderException when the request has no order")
    void calculate_nullOrder_throwsInvalidOrderException() {
        // given
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(null);

        // when / then
        assertThrows(InvalidOrderException.class, () -> pricingService.calculate(request));
    }

    @Test
    @DisplayName("Throws InvalidOrderException when the order has no products")
    void calculate_emptyProducts_throwsInvalidOrderException() {
        // given
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of());
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);

        // when / then
        assertThrows(InvalidOrderException.class, () -> pricingService.calculate(request));
    }

    @Test
    @DisplayName("Returns the quantity-times-price total when the order has no promotion")
    void calculate_validOrderNoPromotion_returnsCalculatedTotal() {
        // given — qty=2, price=50.0, no discount → 50.0 * 2 = 100.0
        PriceCalculationRequest request = requestWith(product("p1", 2, 50.0, null));

        // when
        PriceCalculationResponse response = pricingService.calculate(request);

        // then
        assertEquals(100.0, response.getOrder().getPrice(), 0.001);
    }

    @Test
    @DisplayName("Applies the product's percentage discount to the calculated total")
    void calculate_validOrderWithPromotion_appliesDiscount() {
        // given — 20% off on 100.0, qty=2 → unit 80.0, total 160.0
        PriceCalculationRequest request = requestWith(product("p1", 2, 100.0, 20.0));

        // when
        PriceCalculationResponse response = pricingService.calculate(request);

        // then
        assertEquals(160.0, response.getOrder().getPrice(), 0.001);
    }

    @Test
    @DisplayName("Applies the bulk Drools discount when the quantity exceeds five")
    void calculate_qtyAbove5_appliesDroolsDiscount() {
        // given — qty=6, price=10.0, no promotion
        // Drools rule: qty > 5 → price = price * 0.95 → 9.5 per unit, total = 57.0
        PriceCalculationRequest request = requestWith(product("p1", 6, 10.0, null));

        // when
        PriceCalculationResponse response = pricingService.calculate(request);

        // then
        assertEquals(57.0, response.getOrder().getPrice(), 0.001);
    }

    @Test
    @DisplayName("Returns a UUID identifier for each price calculation")
    void calculate_returnsUuidId() {
        // given
        PriceCalculationRequest request = requestWith(product("p1", 1, 10.0, null));

        // when
        PriceCalculationResponse response = pricingService.calculate(request);

        // then
        assertNotNull(response.getId());
        assertEquals(36, response.getId().length()); // UUID string length
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static PriceCalculationRequest requestWith(ProductVO product) {
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(product));
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);
        return request;
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
