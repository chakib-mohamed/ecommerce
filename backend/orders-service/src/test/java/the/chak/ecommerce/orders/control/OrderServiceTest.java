package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.ProductVO;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @InjectMock
    ProductsApiClient productsApiClient;

    @InjectMock
    @RestClient
    PricingApiClient pricingApiClient;

    @BeforeEach
    void cleanup() {
        Order.deleteAll();
    }

    // ── saveOrder ──────────────────────────────────────────────────────────

    @Test
    void saveOrder_productNotFound_throwsProductNotFoundException() {
        // given
        when(productsApiClient.getProduct("missing-prod")).thenReturn(null);
        Order order = newOrder("missing-prod", 1);

        // when / then
        assertThrows(ProductNotFoundException.class, () -> orderService.saveOrder(order));
    }

    @Test
    void saveOrder_productWithActivePromotion_setsPercentageOff() {
        // given
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(15.0);
        promo.setActiveFrom(LocalDate.now().minusDays(1));
        promo.setActiveTo(LocalDate.now().plusDays(1));

        ProductDto product = productDto("Widget", 50.0, List.of(promo));
        when(productsApiClient.getProduct("prod-1")).thenReturn(product);
        mockPricingResult(75.0);

        Order order = newOrder("prod-1", 2);

        // when
        Order saved = orderService.saveOrder(order);

        // then
        assertNotNull(saved);
        assertEquals(15.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
        assertEquals(75.0, saved.getPrice(), 0.001);
        assertEquals(OrderStatus.INITIATED, saved.getStatus());
    }

    @Test
    void saveOrder_productWithInactivePromotion_ignoresPromotion() {
        // given — promotion dates both in the past
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(20.0);
        promo.setActiveFrom(LocalDate.now().minusDays(10));
        promo.setActiveTo(LocalDate.now().minusDays(1));

        ProductDto product = productDto("OldSale", 30.0, List.of(promo));
        when(productsApiClient.getProduct("prod-2")).thenReturn(product);
        mockPricingResult(30.0);

        Order order = newOrder("prod-2", 1);

        // when
        Order saved = orderService.saveOrder(order);

        // then — expired promotion contributes 0 to percentageOff sum
        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    void saveOrder_productWithNullPromotionDates_ignoresPromotion() {
        // given — activeFrom and activeTo are both null
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(10.0);
        promo.setActiveFrom(null);
        promo.setActiveTo(null);

        ProductDto product = productDto("NoDates", 20.0, List.of(promo));
        when(productsApiClient.getProduct("prod-3")).thenReturn(product);
        mockPricingResult(20.0);

        Order order = newOrder("prod-3", 1);

        // when
        Order saved = orderService.saveOrder(order);

        // then — null dates → isPromotionActive returns false → sum is 0
        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    void saveOrder_productWithNullPromotions_setsNullPercentageOff() {
        // given — product has no promotions at all
        ProductDto product = productDto("NoPromo", 40.0, null);
        when(productsApiClient.getProduct("prod-4")).thenReturn(product);
        mockPricingResult(40.0);

        Order order = newOrder("prod-4", 1);

        // when
        Order saved = orderService.saveOrder(order);

        // then — null promotions list → Optional.ofNullable returns empty → orElse(null)
        assertNull(saved.getProducts().get(0).getPercentageOff());
    }

    // ── searchOrders ───────────────────────────────────────────────────────

    @Test
    void searchOrders_withUserId_returnsAllMatchingOrders() {
        // given — two orders for same user, one for another user
        persistOrder("user-match");
        persistOrder("user-match");
        persistOrder("user-other");

        SearchOrdersCommand cmd = new SearchOrdersCommand();
        cmd.setUserID("user-match");

        // when
        Tuple<Long, List<Order>> result = orderService.searchOrders(cmd);

        // then
        assertEquals(2L, result.getX());
        assertEquals(2, result.getY().size());
    }

    @Test
    void searchOrders_withUserId_filtersResults() {
        // given
        persistOrder("user-filter");
        persistOrder("user-other");

        SearchOrdersCommand cmd = new SearchOrdersCommand();
        cmd.setUserID("user-filter");

        // when
        Tuple<Long, List<Order>> result = orderService.searchOrders(cmd);

        // then
        assertEquals(1L, result.getX());
        assertEquals("user-filter", result.getY().get(0).getUserID());
    }

    @Test
    void searchOrders_withPagination_respectsOffsetAndLimit() {
        // given — 3 orders
        persistOrder("user-page");
        persistOrder("user-page");
        persistOrder("user-page");

        SearchOrdersCommand cmd = new SearchOrdersCommand();
        cmd.setUserID("user-page");
        cmd.setOffset(0);
        cmd.setLimit(2);

        // when
        Tuple<Long, List<Order>> result = orderService.searchOrders(cmd);

        // then — total is 3 but page returns at most 2
        assertEquals(3L, result.getX());
        assertEquals(2, result.getY().size());
    }

    // ── confirmOrder ───────────────────────────────────────────────────────

    @Test
    void confirmOrder_existingOrder_changesStatusToConfirmed() {
        // given
        Order order = persistOrder("user-confirm");
        String orderId = order.id.toString();

        // when
        Order confirmed = orderService.confirmOrder(orderId);

        // then
        assertNotNull(confirmed);
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void confirmOrder_nonExistentOrderId_returnsNull() {
        // given — a random valid ObjectId that doesn't exist
        String fakeId = new org.bson.types.ObjectId().toString();

        // when
        Order result = orderService.confirmOrder(fakeId);

        // then
        assertNull(result);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Order newOrder(String productId, int qty) {
        ProductVO item = new ProductVO();
        item.setProductID(productId);
        item.setQty(qty);

        Order order = new Order();
        order.setUserID("test-user");
        order.setProducts(List.of(item));
        return order;
    }

    private static Order persistOrder(String userId) {
        Order order = new Order();
        order.setUserID(userId);
        order.setStatus(OrderStatus.INITIATED);
        order.persist();
        return order;
    }

    private static ProductDto productDto(String title, double price, List<PromotionDto> promotions) {
        ProductDto dto = new ProductDto();
        dto.setTitle(title);
        dto.setPrice(price);
        dto.setPromotions(promotions);
        return dto;
    }

    private void mockPricingResult(double price) {
        PricingResult.PricingResultOrder resultOrder = new PricingResult.PricingResultOrder();
        resultOrder.setPrice(price);
        PricingResult pricingResult = new PricingResult();
        pricingResult.setOrder(resultOrder);
        pricingResult.setId("process-id");

        Response mockResponse = mock(Response.class);
        when(mockResponse.readEntity(PricingResult.class)).thenReturn(pricingResult);
        when(pricingApiClient.calculatePrice(any())).thenReturn(mockResponse);
    }
}
