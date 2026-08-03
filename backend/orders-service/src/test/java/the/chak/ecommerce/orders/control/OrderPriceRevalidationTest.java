package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.control.exceptions.OrderPriceChangedException;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.ProductVO;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;

/**
 * An order is priced when it is created and may sit unconfirmed indefinitely, while the catalog
 * moves underneath it. Confirming therefore re-reads the live prices instead of trusting the quote.
 *
 * <p>What matters here is that a moved price is refused rather than silently billed at the old
 * figure, and that the refusal says what changed - a bare conflict leaves a buyer with no way to
 * understand why their order stopped.
 */
class OrderPriceRevalidationTest {

    private final ProductsApiClient productsApiClient = mock(ProductsApiClient.class);

    private OrderService service() {
        OrderService service = new OrderService();
        service.productsApiClient = productsApiClient;
        service.meterRegistry = new SimpleMeterRegistry();
        service.stateMachine = new OrderStateMachine();
        return service;
    }

    @Test
    @DisplayName("Accepts the confirmation when the catalog still agrees with the quoted price")
    void pricesUnchanged_isAccepted() {
        // given
        catalogHas("p1", 49.99, null);
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when / then
        assertDoesNotThrow(() -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Refuses the confirmation when a product's price has moved")
    void unitPriceMoved_isRefused() {
        // given - quoted at 49.99, the catalog now says 59.99
        catalogHas("p1", 59.99, null);
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when / then
        assertThrows(OrderPriceChangedException.class,
                () -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Names the product and both prices when refusing")
    void refusal_namesProductAndBothPrices() {
        // given
        catalogHas("p1", 59.99, null);
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when
        OrderPriceChangedException thrown = assertThrows(OrderPriceChangedException.class,
                () -> service().assertPricesUnchanged(order));

        // then - the buyer has to be able to see what changed
        String message = thrown.getMessage();
        assertTrue(message.contains("Lamp"), "should name the product: " + message);
        assertTrue(message.contains("49.99"), "should give the quoted price: " + message);
        assertTrue(message.contains("59.99"), "should give the current price: " + message);
    }

    @Test
    @DisplayName("Refuses the confirmation when a promotion has appeared since the order was placed")
    void discountAppeared_isRefused() {
        // given - same list price, but the product is now discounted, so the total would differ
        catalogHas("p1", 49.99, activePromotion(10.0));
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when / then
        assertThrows(OrderPriceChangedException.class,
                () -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Refuses the confirmation when the promotion the order was quoted under has ended")
    void discountWithdrawn_isRefused() {
        // given
        catalogHas("p1", 49.99, null);
        Order order = orderOf(line("p1", "Lamp", 49.99, 10.0));

        // when / then
        assertThrows(OrderPriceChangedException.class,
                () -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Accepts the confirmation when an unchanged promotion still applies")
    void discountUnchanged_isAccepted() {
        // given
        catalogHas("p1", 49.99, activePromotion(10.0));
        Order order = orderOf(line("p1", "Lamp", 49.99, 10.0));

        // when / then
        assertDoesNotThrow(() -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Ignores a promotion that is not currently running when revalidating")
    void expiredPromotionOnCatalog_isIgnored() {
        // given - the catalog carries a promotion whose window has closed; it must not count
        PromotionDto expired = new PromotionDto();
        expired.setPercentageOff(10.0);
        expired.setActiveFrom(LocalDate.now().minusDays(10));
        expired.setActiveTo(LocalDate.now().minusDays(1));
        catalogHas("p1", 49.99, expired);
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when / then
        assertDoesNotThrow(() -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Refuses the confirmation when a product in the order no longer exists")
    void productRemoved_isRefused() {
        // given
        when(productsApiClient.getProduct("p1")).thenReturn(null);
        Order order = orderOf(line("p1", "Lamp", 49.99, null));

        // when / then
        assertThrows(ProductNotFoundException.class,
                () -> service().assertPricesUnchanged(order));
    }

    @Test
    @DisplayName("Reports every changed product, not only the first")
    void severalChanges_areAllReported() {
        // given
        catalogHas("p1", 59.99, null);
        catalogHas("p2", 15.00, null);
        Order order = orderOf(line("p1", "Lamp", 49.99, null), line("p2", "Rug", 10.00, null));

        // when
        OrderPriceChangedException thrown = assertThrows(OrderPriceChangedException.class,
                () -> service().assertPricesUnchanged(order));

        // then
        assertTrue(thrown.getMessage().contains("Lamp"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Rug"), thrown.getMessage());
    }

    @Test
    @DisplayName("Accepts an order that carries no lines to revalidate")
    void orderWithoutLines_isAccepted() {
        // given - an order whose product list was never populated; nothing to re-check, and it
        // must not fail on the way past
        Order order = new Order();
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("owner");

        // when / then
        assertDoesNotThrow(() -> service().assertPricesUnchanged(order));
    }

    // -- helpers ------------------------------------------------------------

    private void catalogHas(String productId, Double price, PromotionDto promotion) {
        ProductDto dto = new ProductDto();
        dto.setTitle("p1".equals(productId) ? "Lamp" : "Rug");
        dto.setPrice(price);
        dto.setPromotions(promotion == null ? null : List.of(promotion));
        when(productsApiClient.getProduct(productId)).thenReturn(dto);
    }

    private static PromotionDto activePromotion(double percentageOff) {
        PromotionDto promotion = new PromotionDto();
        promotion.setPercentageOff(percentageOff);
        promotion.setActiveFrom(LocalDate.now().minusDays(1));
        promotion.setActiveTo(LocalDate.now().plusDays(1));
        return promotion;
    }

    private static ProductVO line(String productId, String title, Double price, Double percentageOff) {
        ProductVO vo = new ProductVO();
        vo.setProductID(productId);
        vo.setTitle(title);
        vo.setQty(1);
        vo.setPrice(price);
        vo.setPercentageOff(percentageOff);
        return vo;
    }

    private static Order orderOf(ProductVO... lines) {
        Order order = new Order();
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("owner");
        order.setProducts(new ArrayList<>(List.of(lines)));
        return order;
    }
}
