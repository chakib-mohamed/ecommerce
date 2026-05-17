package chakmed.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import chakmed.ecommerce.orders.boundary.command.OrderRequest;
import chakmed.ecommerce.orders.boundary.command.SearchOrdersCommand;
import chakmed.ecommerce.orders.control.PricingApiClient;
import chakmed.ecommerce.orders.control.ProductsApiClient;
import chakmed.ecommerce.orders.control.command.PriceRequest;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;
import chakmed.ecommerce.orders.entity.OrderStatus;
import chakmed.ecommerce.orders.entity.ProductVO;
import chakmed.ecommerce.orders.MongoTestResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import the.chak.products.boundary.dto.ProductDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
public class OrdersResourceTest {

    @InjectMock
    ProductsApiClient productsApiClient;

    @InjectMock
    @RestClient
    PricingApiClient pricingApi;

    @Test
    public void testUpdateOrder() {
        // 1. Create an order first
        Order order = new Order();
        LocalDateTime originalCreationDate = LocalDateTime.now().minusDays(1);
        order.setCreationDate(originalCreationDate);
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("original_user");
        order.setPrice(100.0);
        order.setProducts(new ArrayList<>());
        order.persist();

        String orderId = order.id.toString();

        // 2. Create update request (Note: OrderRequest only has id and products)
        OrderRequest updateRequest = new OrderRequest();
        updateRequest.setId(orderId);
        ProductVO product = new ProductVO();
        product.setProductID("prod_update");
        product.setTitle("Updated Product");
        product.setQty(3);
        product.setPrice(75.0);
        updateRequest.setProducts(List.of(product));

        // 3. Call PUT endpoint
        given().contentType(ContentType.JSON).body(updateRequest).when().put("/orders").then()
                .statusCode(200).body("status", is(OrderStatus.INITIATED.name()))
                .body("userID", is("original_user")).body("price", is(100.0f));

        // 4. Verify in DB
        Order updatedOrder = Order.findById(order.id);
        Assertions.assertEquals(OrderStatus.INITIATED, updatedOrder.getStatus());
        Assertions.assertEquals("original_user", updatedOrder.getUserID());
        Assertions.assertEquals(100.0, updatedOrder.getPrice());
        Assertions.assertEquals(
                originalCreationDate.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                updatedOrder.getCreationDate().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        Assertions.assertEquals(1, updatedOrder.getProducts().size());
        Assertions.assertEquals("prod_update", updatedOrder.getProducts().get(0).getProductID());
    }

    @Test
    public void testCreateOrder() {
        // Mock ProductsApiClient
        ProductDto mockProduct = new ProductDto();
        mockProduct.setTitle("Mock Product");
        mockProduct.setPrice(50.0);
        when(productsApiClient.getProduct(any())).thenReturn(mockProduct);

        // Mock PricingApiClient
        PriceRequest mockPriceResponse = new PriceRequest();
        OrderDTO mockOrderDto = new OrderDTO();
        mockOrderDto.setPrice(100.0);
        mockPriceResponse.setOrder(mockOrderDto);
        mockPriceResponse.setId("process123");
        when(pricingApi.calculatePrice(any())).thenReturn(Response.ok(mockPriceResponse).build());

        OrderRequest request = new OrderRequest();
        ProductVO product = new ProductVO();
        product.setProductID("prod1");
        product.setTitle("Product 1");
        product.setQty(2);
        product.setPrice(50.0);
        request.setProducts(List.of(product));

        String orderId = given().contentType(ContentType.JSON).body(request).when().post("/orders")
                .then().statusCode(201).body("price", is(100.0f)).extract().path("id");

        Assertions.assertNotNull(orderId);
        Order order = Order.findById(new ObjectId(orderId));
        Assertions.assertNotNull(order);
    }

    @Test
    public void testSearchOrders() {
        // Setup: Create some orders
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

        given().contentType(ContentType.JSON).body(command).when().post("/orders/search").then()
                .statusCode(200).body("y", not(empty())).body("y[0].userID", is("user_search"));
    }

    @Test
    public void testDeleteOrder() {
        // Setup: Create an order
        Order order = new Order();
        order.setUserID("user_delete");
        order.setStatus(OrderStatus.INITIATED);
        order.persist();
        String orderId = order.id.toString();

        // Delete
        given().when().delete("/orders/" + orderId).then().statusCode(200);

        // Verify
        Order deletedOrder = Order.findById(order.id);
        Assertions.assertNull(deletedOrder);
    }

    @Test
    public void testConfirmOrder() {
        // 1. Create an order
        Order order = new Order();
        order.setUserID("user_confirm");
        order.setStatus(OrderStatus.INITIATED);
        order.persist();
        String orderId = order.id.toString();

        // 2. Call POST /orders/{id}/confirm
        given().when().post("/orders/" + orderId + "/confirm").then().statusCode(200).body("status",
                is(OrderStatus.CONFIRMED.name()));

        // 3. Verify in DB
        Order confirmedOrder = Order.findById(order.id);
        Assertions.assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
    }
}
