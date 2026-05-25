package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class ApplyPromotionsServiceTest {

    @Inject
    ApplyPromotionsService applyPromotionsService;

    @Test
    void applyPromotion_withPercentageOff_appliesDiscountToTotal() {
        // given — 10% off on 100.0, qty=2 → 90.0 * 2 = 180.0
        OrderDTO order = orderWith(product("p1", 2, 100.0, 10.0));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(180.0, result.getPrice(), 0.001);
        assertEquals(90.0, result.getProducts().get(0).getPrice(), 0.001);
    }

    @Test
    void applyPromotion_withoutPercentageOff_usesFullPrice() {
        // given — no discount, price=50.0, qty=3 → 150.0
        OrderDTO order = orderWith(product("p1", 3, 50.0, null));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(150.0, result.getPrice(), 0.001);
        assertEquals(50.0, result.getProducts().get(0).getPrice(), 0.001);
    }

    @Test
    void applyPromotion_multipleProducts_sumsTotals() {
        // given — product1: qty=1, price=100.0, no discount → 100.0
        //          product2: qty=2, price=50.0, 50% off → 25.0 * 2 = 50.0
        //          total = 150.0
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
    void applyPromotion_priceRoundedToTwoDecimalPlaces() {
        // given — 33.33% off on 10.0 → unit price ≈ 6.667, qty=3 → 20.001 → rounds to 20.0
        OrderDTO order = orderWith(product("p1", 3, 10.0, 33.33));

        // when
        OrderDTO result = applyPromotionsService.applyPromotion(order);

        // then
        assertEquals(20.0, result.getPrice(), 0.005);
    }

    // ── helpers ────────────────────────────────────────────────────────────

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
