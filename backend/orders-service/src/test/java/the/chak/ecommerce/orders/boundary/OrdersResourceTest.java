package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.control.PricingApiClient;
import the.chak.ecommerce.orders.control.PricingResult;
import the.chak.ecommerce.orders.control.ProductsApiClient;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.repository.OrderRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OrdersResourceTest {

    @InjectMock
    ProductsApiClient productsApiClient;

    @InjectMock
    @RestClient
    PricingApiClient pricingApi;

    @Inject
    OrderRepository orderRepository;

    @BeforeEach
    void cleanup() {
        orderRepository.deleteAll();
    }

    @Test
    @TestSecurity(user = "original_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "original_user") })
    @DisplayName("Preserves the status, price, owner, and creation date when updating an initiated order")
    void updateOrder_initiatedOrder_preservesStatusAndPrice() {
        // given
        LocalDateTime originalCreationDate = LocalDateTime.now().minusDays(1);
        Order order = new Order();
        order.setCreationDate(originalCreationDate);
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("original_user");
        order.setPrice(100.0);
        order.setProducts(new ArrayList<>());
        orderRepository.persist(order);

        String body = String.format(
                "{\"id\":\"%s\",\"products\":[{\"product_id\":\"prod_update\",\"title\":\"Updated Product\",\"qty\":3,\"price\":75.0}]}",
                order.id.toString());

        // when
        var response = given().contentType(ContentType.JSON).body(body)
                .when().put("/orders");

        // then
        response.then().statusCode(200)
                .body("status", is(OrderStatus.INITIATED.name()))
                .body("user_id", is("original_user"))
                .body("price", is(100.0f));

        Order updated = orderRepository.findById(order.id);
        assertEquals(OrderStatus.INITIATED, updated.getStatus());
        assertEquals("original_user", updated.getUserID());
        assertEquals(100.0, updated.getPrice());
        assertEquals(
                originalCreationDate.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                updated.getCreationDate().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertEquals(1, updated.getProducts().size());
        assertEquals("prod_update", updated.getProducts().get(0).getProductID());
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 201 with the calculated price and persists the order owned by the authenticated user for valid products")
    void createOrder_validProducts_returns201WithCalculatedPrice() {
        // given
        ProductDto mockProduct = new ProductDto();
        mockProduct.setTitle("Mock Product");
        mockProduct.setPrice(50.0);
        when(productsApiClient.getProduct(any())).thenReturn(mockProduct);

        PricingResult.PricingResultOrder mockResultOrder = new PricingResult.PricingResultOrder();
        mockResultOrder.setPrice(100.0);
        PricingResult mockPricingResult = new PricingResult();
        mockPricingResult.setOrder(mockResultOrder);
        mockPricingResult.setId("process123");
        Response mockPricingResponse = mock(Response.class);
        when(mockPricingResponse.readEntity(PricingResult.class)).thenReturn(mockPricingResult);
        when(pricingApi.calculatePrice(any())).thenReturn(mockPricingResponse);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"products\":[{\"product_id\":\"prod1\",\"title\":\"Product 1\",\"qty\":2,\"price\":50.0}]}")
                .when().post("/orders");

        // then
        String orderId = response.then().statusCode(201).body("price", is(100.0f))
                .body("user_id", is("test_user"))
                .extract().path("id");
        assertNotNull(orderId);
        Order persisted = orderRepository.findById(new ObjectId(orderId));
        assertNotNull(persisted);
        assertEquals("test_user", persisted.getUserID());
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns only the orders belonging to the requested user when searching by user id")
    void searchOrders_byUserId_returnsMatchingOrders() {
        // given
        Order order1 = new Order();
        order1.setUserID("user_search");
        order1.setStatus(OrderStatus.INITIATED);
        orderRepository.persist(order1);

        Order order2 = new Order();
        order2.setUserID("user_other");
        order2.setStatus(OrderStatus.INITIATED);
        orderRepository.persist(order2);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"user_id\":\"user_search\"}")
                .when().post("/orders/search");

        // then
        response.then().statusCode(200)
                .body("y", not(empty()))
                .body("y[0].user_id", is("user_search"));
    }

    @Test
    @TestSecurity(user = "user_delete")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "user_delete") })
    @DisplayName("Returns 200 and removes the order when deleting an existing order")
    void deleteOrder_existingOrder_removesFromDatabase() {
        // given
        Order order = new Order();
        order.setUserID("user_delete");
        order.setStatus(OrderStatus.INITIATED);
        orderRepository.persist(order);
        String orderId = order.id.toString();

        // when
        var response = given().when().delete("/orders/" + orderId);

        // then
        response.then().statusCode(200);
        assertNull(orderRepository.findById(order.id));
    }

    @Test
    @TestSecurity(user = "user_confirm")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "user_confirm") })
    @DisplayName("Returns 200 with CONFIRMED status when confirming an initiated order")
    void confirmOrder_initiatedOrder_changesStatusToConfirmed() {
        // given
        Order order = new Order();
        order.setUserID("user_confirm");
        order.setStatus(OrderStatus.INITIATED);
        orderRepository.persist(order);
        String orderId = order.id.toString();

        // when
        var response = given().when().post("/orders/" + orderId + "/confirm");

        // then
        response.then().statusCode(200).body("status", is(OrderStatus.CONFIRMED.name()));
        Order confirmed = orderRepository.findById(order.id);
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 400 with VALIDATION_ERROR when creating an order with no products")
    void createOrder_emptyProductsList_returns400() {
        // given
        OrderRequest request = new OrderRequest();
        request.setProducts(List.of());

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/orders");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 400 with VALIDATION_ERROR when an ordered product has a blank id")
    void createOrder_productWithBlankId_returns400() {
        // given
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"products\":[{\"product_id\":\"\",\"qty\":1,\"price\":10.0}]}")
                .when().post("/orders");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns only the orders containing the given product when searching with a product id, for purchase verification")
    void searchOrders_byUserIdAndProductId_returnsOnlyOrdersContainingThatProduct() {
        // given
        the.chak.ecommerce.orders.entity.ProductVO matchingProduct =
                new the.chak.ecommerce.orders.entity.ProductVO();
        matchingProduct.setProductID("prod_reviewed");
        Order orderWithProduct = new Order();
        orderWithProduct.setUserID("user_purchase_check");
        orderWithProduct.setStatus(OrderStatus.INITIATED);
        orderWithProduct.setProducts(List.of(matchingProduct));
        orderRepository.persist(orderWithProduct);

        the.chak.ecommerce.orders.entity.ProductVO otherProduct =
                new the.chak.ecommerce.orders.entity.ProductVO();
        otherProduct.setProductID("prod_unrelated");
        Order orderWithoutProduct = new Order();
        orderWithoutProduct.setUserID("user_purchase_check");
        orderWithoutProduct.setStatus(OrderStatus.INITIATED);
        orderWithoutProduct.setProducts(List.of(otherProduct));
        orderRepository.persist(orderWithoutProduct);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"user_id\":\"user_purchase_check\",\"product_id\":\"prod_reviewed\"}")
                .when().post("/orders/search");

        // then
        response.then().statusCode(200)
                .body("x", is(1))
                .body("y.size()", is(1))
                .body("y[0].products[0].product_id", is("prod_reviewed"));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 400 with VALIDATION_ERROR when searching with a blank user id")
    void searchOrders_blankUserId_returns400() {
        // given
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"user_id\":\"\"}")
                .when().post("/orders/search");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    // --ownership and lifecycle guards ---------------------------------------
    // Each order operation refuses in three ways: the order does not exist, it belongs to someone
    // else, or it has moved past the point where the operation still makes sense.

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 404 when updating an order that does not exist")
    void updateOrder_unknownOrder_returns404() {
        String body = String.format(
                "{\"id\":\"%s\",\"products\":[{\"product_id\":\"p\",\"qty\":1,\"price\":1.0}]}",
                new ObjectId());

        given().contentType(ContentType.JSON).body(body)
                .when().put("/orders")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "intruder")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "intruder") })
    @DisplayName("Returns 403 when updating an order belonging to someone else")
    void updateOrder_otherUsersOrder_returns403() {
        Order order = persistedOrder("owner", OrderStatus.INITIATED);
        String body = String.format(
                "{\"id\":\"%s\",\"products\":[{\"product_id\":\"p\",\"qty\":1,\"price\":1.0}]}", order.id);

        given().contentType(ContentType.JSON).body(body)
                .when().put("/orders")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "owner") })
    @DisplayName("Returns 409 when updating an order that has already been confirmed")
    void updateOrder_confirmedOrder_returns409() {
        Order order = persistedOrder("owner", OrderStatus.CONFIRMED);
        String body = String.format(
                "{\"id\":\"%s\",\"products\":[{\"product_id\":\"p\",\"qty\":1,\"price\":1.0}]}", order.id);

        given().contentType(ContentType.JSON).body(body)
                .when().put("/orders")
                .then().statusCode(409)
                .body("error_code", is("ORDER_NOT_MUTABLE"));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 404 when deleting an order that does not exist")
    void deleteOrder_unknownOrder_returns404() {
        given().when().delete("/orders/" + new ObjectId()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "intruder")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "intruder") })
    @DisplayName("Returns 403 when deleting an order belonging to someone else")
    void deleteOrder_otherUsersOrder_returns403() {
        Order order = persistedOrder("owner", OrderStatus.INITIATED);

        given().when().delete("/orders/" + order.id).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "owner") })
    @DisplayName("Returns 409 when deleting an order that has already been confirmed")
    void deleteOrder_confirmedOrder_returns409() {
        Order order = persistedOrder("owner", OrderStatus.CONFIRMED);

        given().when().delete("/orders/" + order.id)
                .then().statusCode(409)
                .body("error_code", is("ORDER_NOT_MUTABLE"));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 404 when confirming an order that does not exist")
    void confirmOrder_unknownOrder_returns404() {
        given().when().post("/orders/" + new ObjectId() + "/confirm").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "intruder")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "intruder") })
    @DisplayName("Returns 403 when confirming an order belonging to someone else")
    void confirmOrder_otherUsersOrder_returns403() {
        Order order = persistedOrder("owner", OrderStatus.INITIATED);

        given().when().post("/orders/" + order.id + "/confirm").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "owner") })
    @DisplayName("Returns 409 when confirming an order that is already confirmed")
    void confirmOrder_alreadyConfirmed_returns409() {
        Order order = persistedOrder("owner", OrderStatus.CONFIRMED);

        given().when().post("/orders/" + order.id + "/confirm")
                .then().statusCode(409)
                .body("error_code", is("ILLEGAL_ORDER_TRANSITION"));
    }

    // --cancel ---------------------------------------------------------------

    @Test
    @TestSecurity(user = "owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "owner") })
    @DisplayName("Returns 200 with CANCELLED status when cancelling a confirmed order")
    void cancelOrder_confirmedOrder_returns200() {
        Order order = persistedOrder("owner", OrderStatus.CONFIRMED);

        given().when().post("/orders/" + order.id + "/cancel")
                .then().statusCode(200)
                .body("status", is(OrderStatus.CANCELLED.name()));

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(order.id).getStatus());
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    @DisplayName("Returns 404 when cancelling an order that does not exist")
    void cancelOrder_unknownOrder_returns404() {
        given().when().post("/orders/" + new ObjectId() + "/cancel").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "intruder")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "intruder") })
    @DisplayName("Returns 403 when cancelling an order belonging to someone else")
    void cancelOrder_otherUsersOrder_returns403() {
        Order order = persistedOrder("owner", OrderStatus.INITIATED);

        given().when().post("/orders/" + order.id + "/cancel").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "owner") })
    @DisplayName("Returns 409 when cancelling an order that has already been cancelled")
    void cancelOrder_alreadyCancelled_returns409() {
        Order order = persistedOrder("owner", OrderStatus.CANCELLED);

        given().when().post("/orders/" + order.id + "/cancel")
                .then().statusCode(409)
                .body("error_code", is("ILLEGAL_ORDER_TRANSITION"));
    }

    private Order persistedOrder(String userId, OrderStatus status) {
        Order order = new Order();
        order.setCreationDate(LocalDateTime.now());
        order.setStatus(status);
        order.setUserID(userId);
        order.setPrice(10.0);
        order.setProducts(new ArrayList<>());
        orderRepository.persist(order);
        return order;
    }
}
