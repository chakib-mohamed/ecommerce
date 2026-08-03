package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.types.ObjectId;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import the.chak.ecommerce.orders.control.exceptions.IllegalOrderTransitionException;
import the.chak.ecommerce.orders.control.exceptions.OrderNotMutableException;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.ProductVO;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OrderSearch;
import the.chak.ecommerce.orders.repository.PagedResult;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    OrderService orderService;

    @Mock
    ProductsApiClient productsApiClient;

    @Mock
    PricingApiClient pricingApiClient;

    @Mock
    OrderRepository orderRepository;

    // A real registry (not a mock) so meter increments are actually recorded and assertable.
    // @InjectMocks injects @Spy fields, so OrderService receives this registry.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // The real state machine, not a mock: these tests are about the guards actually refusing
    // illegal moves, which a stubbed machine would not exercise.
    @Spy
    OrderStateMachine stateMachine = new OrderStateMachine();

    // --saveOrder ----------------------------------------------------------

    @Test
    @DisplayName("Throws ProductNotFoundException when an ordered product cannot be found")
    void saveOrder_productNotFound_throwsProductNotFoundException() {
        // given
        when(productsApiClient.getProduct("missing-prod")).thenReturn(null);
        Order order = newOrder("missing-prod", 1);

        // when & then
        assertThrows(ProductNotFoundException.class, () -> orderService.saveOrder(order));
    }

    @Test
    @DisplayName("Counts the created order and records its value on a successful save")
    void saveOrder_success_recordsCreatedCounterAndValue() {
        // given
        ProductDto product = productDto("Widget", 50.0, null);
        when(productsApiClient.getProduct("prod-1")).thenReturn(product);
        mockPricingResult(120.0);
        Order order = newOrder("prod-1", 2);

        // when
        orderService.saveOrder(order);

        // then
        assertEquals(1.0, meterRegistry.get("orders.created").counter().count(), 0.001);
        assertEquals(1L, meterRegistry.get("order.value.amount").summary().count());
        assertEquals(120.0, meterRegistry.get("order.value.amount").summary().totalAmount(), 0.001);
    }

    @Test
    @DisplayName("Records no order-created metric when the product cannot be found")
    void saveOrder_productNotFound_doesNotRecordCreatedMetric() {
        // given
        when(productsApiClient.getProduct("missing-prod")).thenReturn(null);
        Order order = newOrder("missing-prod", 1);

        // when & then
        assertThrows(ProductNotFoundException.class, () -> orderService.saveOrder(order));
        assertNull(meterRegistry.find("orders.created").counter());
    }

    @Test
    @DisplayName("Applies the discount percentage and calculated price when the product has an active promotion")
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
        verify(orderRepository).persist(saved);
    }

    @Test
    @DisplayName("Ignores the promotion and leaves the discount at zero when the promotion is not active")
    void saveOrder_productWithInactivePromotion_ignoresPromotion() {
        // given
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

        // then
        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Wraps the pricing request products in an 'order' envelope so price-service can read them")
    void saveOrder_pricingRequest_wrapsProductsInOrderEnvelope() {
        // given
        ProductDto product = productDto("Widget", 40.0, null);
        when(productsApiClient.getProduct("prod-1")).thenReturn(product);

        // capture the exact body orders serializes onto the wire to price-service
        String[] sentBody = new String[1];
        Response mockResponse = mock(Response.class);
        when(mockResponse.readEntity(PricingResult.class)).thenReturn(pricingResult(120.0));
        when(pricingApiClient.calculatePrice(any())).thenAnswer(inv -> {
            sentBody[0] = pricingJsonb().toJson(inv.getArgument(0));
            return mockResponse;
        });

        Order order = newOrder("prod-1", 3);

        // when
        orderService.saveOrder(order);

        // then - price-service expects { "order": { "products": [ ... ] } }; a bare
        // { "products": [ ... ] } leaves its 'order' null and is rejected with 400.
        JsonObject body = Json.createReader(new StringReader(sentBody[0])).readObject();
        assertTrue(body.containsKey("order"),
                "pricing request must wrap products in an 'order' object; was: " + sentBody[0]);
        JsonArray products = body.getJsonObject("order").getJsonArray("products");
        assertNotNull(products, "order.products must be present; was: " + sentBody[0]);
        assertEquals(1, products.size());
        assertEquals(3, products.getJsonObject(0).getInt("qty"));
        assertEquals(40.0, products.getJsonObject(0).getJsonNumber("price").doubleValue(), 0.001);
    }

    // --searchOrders -------------------------------------------------------

    @Test
    @DisplayName("Returns the count and matching orders when searching by user id")
    void searchOrders_withUserId_filtersResults() {
        // given
        SearchOrdersCommand cmd = new SearchOrdersCommand();
        cmd.setUserID("user-1");

        PagedResult<Order> expected = new PagedResult<>(1L, List.of(new Order()));
        when(orderRepository.search(any(OrderSearch.class))).thenReturn(expected);

        // when
        Tuple<Long, List<Order>> result = orderService.searchOrders(cmd);

        // then
        assertEquals(1L, result.getX());
        assertEquals(1, result.getY().size());
    }

    // --confirmOrder -------------------------------------------------------
    // The happy path now commits the CONFIRMED order and a matching outbox entry inside a Mongo
    // transaction, so it is exercised against real Testcontainers in OrderOutboxWritePathTest rather
    // than mocked here. Only the early-return path (before any transaction) stays a unit test.

    @Test
    @DisplayName("Returns null when confirming an order id that does not exist")
    void confirmOrder_nonExistentOrderId_returnsNull() {
        // given
        String fakeId = new ObjectId().toString();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(null);

        // when
        Order result = orderService.confirmOrder(fakeId);

        // then
        assertNull(result);
    }

    @Test
    @DisplayName("Records no orders-confirmed metric when the order id does not exist")
    void confirmOrder_nonExistentOrderId_doesNotIncrementConfirmedCounter() {
        // given
        String fakeId = new ObjectId().toString();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(null);

        // when
        orderService.confirmOrder(fakeId);

        // then
        assertNull(meterRegistry.find("orders.confirmed").counter());
    }

    // --confirmOrder: lifecycle guards --------------------------------------
    // Confirming is only legal from INITIATED. Without this guard a second confirm rewrites the
    // status and inserts a second outbox entry, so `order-initiated` is published twice and any
    // consumer that is not idempotent double-processes the sale.

    @Test
    @DisplayName("Refuses to confirm an order that has already been confirmed")
    void confirmOrder_alreadyConfirmed_isRejected() {
        // given
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.CONFIRMED);
        order.id = new ObjectId();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);

        // when / then
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.confirmOrder(order.id.toString()));
    }

    @Test
    @DisplayName("Refuses to confirm an order that has been cancelled")
    void confirmOrder_cancelled_isRejected() {
        // given
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.CANCELLED);
        order.id = new ObjectId();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);

        // when / then
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.confirmOrder(order.id.toString()));
    }

    @Test
    @DisplayName("Records no orders-confirmed metric when a second confirmation is refused")
    void confirmOrder_alreadyConfirmed_doesNotIncrementConfirmedCounter() {
        // given
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.CONFIRMED);
        order.id = new ObjectId();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);

        // when
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.confirmOrder(order.id.toString()));

        // then - a refused confirmation is not a confirmation
        assertNull(meterRegistry.find("orders.confirmed").counter());
    }

    // --cancelOrder ---------------------------------------------------------

    @Test
    @DisplayName("Cancels an order that has not been confirmed yet")
    void cancelOrder_initiated_movesToCancelled() {
        // given
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.INITIATED);
        order.id = new ObjectId();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);

        // when
        Order cancelled = orderService.cancelOrder(order.id.toString());

        // then
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    @DisplayName("Refuses to cancel an order that has already shipped")
    void cancelOrder_shipped_isRejected() {
        // given
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.SHIPPED);
        order.id = new ObjectId();
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);

        // when / then
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.cancelOrder(order.id.toString()));
    }

    @Test
    @DisplayName("Returns null when cancelling an order id that does not exist")
    void cancelOrder_nonExistentOrderId_returnsNull() {
        // given
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(null);

        // when
        Order result = orderService.cancelOrder(new ObjectId().toString());

        // then
        assertNull(result);
    }

    // --promotion windows ---------------------------------------------------
    // A promotion with an open-ended window is not a promotion the order can price against, and a
    // window is checked at both ends. These are the guards the discount calculation leans on.

    @Test
    @DisplayName("Ignores a promotion that has no start date")
    void saveOrder_promotionWithoutStartDate_isIgnored() {
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveTo(LocalDate.now().plusDays(5));

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 10.0, List.of(promo)));
        mockPricingResult(10.0);

        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Ignores a promotion that has no end date")
    void saveOrder_promotionWithoutEndDate_isIgnored() {
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveFrom(LocalDate.now().minusDays(5));

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 10.0, List.of(promo)));
        mockPricingResult(10.0);

        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Ignores a promotion whose window has not opened yet")
    void saveOrder_promotionStartingLater_isIgnored() {
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveFrom(LocalDate.now().plusDays(1));
        promo.setActiveTo(LocalDate.now().plusDays(5));

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 10.0, List.of(promo)));
        mockPricingResult(10.0);

        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    // --stacked discounts ---------------------------------------------------
    // Promotions are summed, so nothing stops two generous ones from exceeding the whole price.
    // Unclamped that yields a negative line total, which the order would then be priced at.

    @Test
    @DisplayName("Caps the combined discount at the full price when promotions stack past 100%")
    void saveOrder_promotionsStackingPastFull_capsDiscountAtFullPrice() {
        // given - 60% and 50% together would be 110% off
        PromotionDto first = activePromotion(60.0);
        PromotionDto second = activePromotion(50.0);

        when(productsApiClient.getProduct("prod-1"))
                .thenReturn(productDto("Widget", 100.0, List.of(first, second)));
        mockPricingResult(0.0);

        // when
        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        // then - the buyer gets it free, never paid to take it
        assertEquals(100.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Leaves a combined discount below the full price untouched")
    void saveOrder_promotionsStackingBelowFull_keepsTheSum() {
        // given
        when(productsApiClient.getProduct("prod-1"))
                .thenReturn(productDto("Widget", 100.0, List.of(activePromotion(20.0), activePromotion(15.0))));
        mockPricingResult(65.0);

        // when
        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        // then
        assertEquals(35.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    // --promotion window boundaries -----------------------------------------
    // A promotion that runs "from today" or "until today" is running today. Excluding the
    // boundary days silently drops the first and last day of every promotion.

    @Test
    @DisplayName("Applies a promotion on the day it starts")
    void saveOrder_promotionStartingToday_isApplied() {
        // given
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveFrom(LocalDate.now());
        promo.setActiveTo(LocalDate.now().plusDays(5));

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 100.0, List.of(promo)));
        mockPricingResult(75.0);

        // when
        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        // then
        assertEquals(25.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Applies a promotion on the day it ends")
    void saveOrder_promotionEndingToday_isApplied() {
        // given
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveFrom(LocalDate.now().minusDays(5));
        promo.setActiveTo(LocalDate.now());

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 100.0, List.of(promo)));
        mockPricingResult(75.0);

        // when
        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        // then
        assertEquals(25.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    @Test
    @DisplayName("Ignores a promotion whose window closed yesterday")
    void saveOrder_promotionEndedYesterday_isIgnored() {
        // given
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(25.0);
        promo.setActiveFrom(LocalDate.now().minusDays(5));
        promo.setActiveTo(LocalDate.now().minusDays(1));

        when(productsApiClient.getProduct("prod-1")).thenReturn(productDto("W", 100.0, List.of(promo)));
        mockPricingResult(100.0);

        // when
        Order saved = orderService.saveOrder(newOrder("prod-1", 1));

        // then
        assertEquals(0.0, saved.getProducts().get(0).getPercentageOff(), 0.001);
    }

    private static PromotionDto activePromotion(double percentageOff) {
        PromotionDto promo = new PromotionDto();
        promo.setPercentageOff(percentageOff);
        promo.setActiveFrom(LocalDate.now().minusDays(1));
        promo.setActiveTo(LocalDate.now().plusDays(1));
        return promo;
    }

    // --assertMutable -------------------------------------------------------

    @Test
    @DisplayName("Allows a change to an order that has not been confirmed yet")
    void assertMutable_initiated_isAllowed() {
        Order order = newOrder("p1", 1);
        order.setStatus(OrderStatus.INITIATED);

        orderService.assertMutable(order);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "INITIATED")
    @DisplayName("Refuses a change to an order that has moved past initiation")
    void assertMutable_pastInitiated_isRejected(OrderStatus status) {
        Order order = newOrder("p1", 1);
        order.setStatus(status);

        assertThrows(OrderNotMutableException.class, () -> orderService.assertMutable(order));
    }

    // --helpers ------------------------------------------------------------

    private static Order newOrder(String productId, int qty) {
        ProductVO item = new ProductVO();
        item.setProductID(productId);
        item.setQty(qty);

        Order order = new Order();
        order.setUserID("test-user");
        order.setProducts(List.of(item));
        return order;
    }

    private static ProductDto productDto(String title, double price, List<PromotionDto> promotions) {
        ProductDto dto = new ProductDto();
        dto.setTitle(title);
        dto.setPrice(price);
        dto.setPromotions(promotions);
        return dto;
    }

    // Mirrors price-service's JSON-B contract (snake_case + null omission) so the captured body
    // is byte-identical to what travels over the wire on the orders -> pricing hop.
    private static Jsonb pricingJsonb() {
        return JsonbBuilder.create(new JsonbConfig()
                .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES)
                .withNullValues(false));
    }

    private static PricingResult pricingResult(double price) {
        PricingResult.PricingResultOrder resultOrder = new PricingResult.PricingResultOrder();
        resultOrder.setPrice(price);
        PricingResult result = new PricingResult();
        result.setOrder(resultOrder);
        result.setId("process-id");
        return result;
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
