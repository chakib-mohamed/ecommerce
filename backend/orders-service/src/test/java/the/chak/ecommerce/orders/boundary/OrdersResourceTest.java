package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.PriceRequest;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.OrderStatus;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.orders.control.PricingApiClient;
import the.chak.ecommerce.orders.control.ProductsApiClient;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.MongoTestResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class OrdersResourceTest {

    @InjectMock
    ProductsApiClient productsApiClient;

    @InjectMock
    @RestClient
    PricingApiClient pricingApi;

    @BeforeEach
    void cleanup() {
        Order.deleteAll();
    }

    @Test
    @TestSecurity(user = "original_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "original_user") })
    void updateOrder_initiatedOrder_preservesStatusAndPrice() {
        // given
        LocalDateTime originalCreationDate = LocalDateTime.now().minusDays(1);
        Order order = new Order();
        order.setCreationDate(originalCreationDate);
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("original_user");
        order.setPrice(100.0);
        order.setProducts(new ArrayList<>());
        order.persist();

        ProductVO product = new ProductVO();
        product.setProductID("prod_update");
        product.setTitle("Updated Product");
        product.setQty(3);
        product.setPrice(75.0);

        OrderRequest request = new OrderRequest();
        request.setId(order.id.toString());
        request.setProducts(List.of(product));

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().put("/orders");

        // then
        response.then().statusCode(200)
                .body("status", is(OrderStatus.INITIATED.name()))
                .body("userID", is("original_user"))
                .body("price", is(100.0f));

        Order updated = Order.findById(order.id);
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
    void createOrder_validProducts_returns201WithCalculatedPrice() {
        // given
        ProductDto mockProduct = new ProductDto();
        mockProduct.setTitle("Mock Product");
        mockProduct.setPrice(50.0);
        when(productsApiClient.getProduct(any())).thenReturn(mockProduct);

        PriceRequest mockPriceResponse = new PriceRequest();
        OrderDTO mockOrderDto = new OrderDTO();
        mockOrderDto.setPrice(100.0);
        mockPriceResponse.setOrder(mockOrderDto);
        mockPriceResponse.setId("process123");
        when(pricingApi.calculatePrice(any())).thenReturn(Response.ok(mockPriceResponse).build());

        ProductVO product = new ProductVO();
        product.setProductID("prod1");
        product.setTitle("Product 1");
        product.setQty(2);
        product.setPrice(50.0);

        OrderRequest request = new OrderRequest();
        request.setProducts(List.of(product));

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/orders");

        // then
        String orderId = response.then().statusCode(201).body("price", is(100.0f))
                .extract().path("id");
        assertNotNull(orderId);
        assertNotNull(Order.findById(new ObjectId(orderId)));
    }

    @Test
    @TestSecurity(user = "test_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "test_user") })
    void searchOrders_byUserId_returnsMatchingOrders() {
        // given
        Order order1 = new Order();
        order1.setUserID("user_search");
        order1.setStatus(OrderStatus.INITIATED);
        order1.persist();

        Order order2 = new Order();
        order2.setUserID("user_other");
        order2.setStatus(OrderStatus.INITIATED);
        order2.persist();

        SearchOrdersCommand command = new SearchOrdersCommand();
        command.setUserID("user_search");

        // when
        var response = given().contentType(ContentType.JSON).body(command)
                .when().post("/orders/search");

        // then
        response.then().statusCode(200)
                .body("y", not(empty()))
                .body("y[0].userID", is("user_search"));
    }

    @Test
    @TestSecurity(user = "user_delete")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "user_delete") })
    void deleteOrder_existingOrder_removesFromDatabase() {
        // given
        Order order = new Order();
        order.setUserID("user_delete");
        order.setStatus(OrderStatus.INITIATED);
        order.persist();
        String orderId = order.id.toString();

        // when
        var response = given().when().delete("/orders/" + orderId);

        // then
        response.then().statusCode(200);
        assertNull(Order.findById(order.id));
    }

    @Test
    @TestSecurity(user = "user_confirm")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "user_confirm") })
    void confirmOrder_initiatedOrder_changesStatusToConfirmed() {
        // given
        Order order = new Order();
        order.setUserID("user_confirm");
        order.setStatus(OrderStatus.INITIATED);
        order.persist();
        String orderId = order.id.toString();

        // when
        var response = given().when().post("/orders/" + orderId + "/confirm");

        // then
        response.then().statusCode(200).body("status", is(OrderStatus.CONFIRMED.name()));
        Order confirmed = Order.findById(order.id);
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }
}
