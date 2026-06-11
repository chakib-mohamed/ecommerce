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
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.types.ObjectId;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
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
