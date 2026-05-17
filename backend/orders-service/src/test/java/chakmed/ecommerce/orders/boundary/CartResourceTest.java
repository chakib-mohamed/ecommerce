package chakmed.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import chakmed.ecommerce.orders.control.OrderService;
import chakmed.ecommerce.orders.control.PriceCacheService;
import chakmed.ecommerce.orders.entity.Cart;
import chakmed.ecommerce.orders.entity.CartItem;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderStatus;
import chakmed.ecommerce.orders.MongoTestResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
public class CartResourceTest {

    static final String USER_ID = "test-user-123";

    @InjectMock
    PriceCacheService priceCacheService;

    @InjectMock
    OrderService orderService;

    @BeforeEach
    void cleanup() {
        Cart.deleteAll();
        when(priceCacheService.getPrice(anyString())).thenReturn(null);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void addItem_createsNewCart() {
        given()
            .contentType(ContentType.JSON)
            .body("{ \"productId\": \"prod-1\", \"quantity\": 2 }")
        .when()
            .post("/cart/items")
        .then()
            .statusCode(201)
            .body("userId", is(USER_ID))
            .body("items", hasSize(1))
            .body("items[0].productId", is("prod-1"))
            .body("items[0].quantity", is(2));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void addItem_incrementsQuantityIfAlreadyPresent() {
        given().contentType(ContentType.JSON)
            .body("{ \"productId\": \"prod-1\", \"quantity\": 2 }")
            .when().post("/cart/items");

        given()
            .contentType(ContentType.JSON)
            .body("{ \"productId\": \"prod-1\", \"quantity\": 3 }")
        .when()
            .post("/cart/items")
        .then()
            .statusCode(201)
            .body("items", hasSize(1))
            .body("items[0].quantity", is(5));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void addItem_returns400WhenQuantityIsZeroOrNegative() {
        given()
            .contentType(ContentType.JSON)
            .body("{ \"productId\": \"prod-1\", \"quantity\": 0 }")
        .when()
            .post("/cart/items")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void addItem_returns400WhenProductIdIsBlank() {
        given()
            .contentType(ContentType.JSON)
            .body("{ \"productId\": \"\", \"quantity\": 1 }")
        .when()
            .post("/cart/items")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void getCart_returns404WhenNoCart() {
        given()
        .when()
            .get("/cart")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void getCart_returnsCartWhenExists() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
        .when()
            .get("/cart")
        .then()
            .statusCode(200)
            .body("userId", is(USER_ID))
            .body("items", hasSize(1))
            .body("items[0].productId", is("prod-1"))
            .body("items[0].quantity", is(3));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void updateItem_changesQuantity() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
            .contentType(ContentType.JSON)
            .body("{ \"quantity\": 10 }")
        .when()
            .put("/cart/items/prod-1")
        .then()
            .statusCode(200)
            .body("items[0].quantity", is(10));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void updateItem_returns404WhenProductNotInCart() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
            .contentType(ContentType.JSON)
            .body("{ \"quantity\": 5 }")
        .when()
            .put("/cart/items/prod-999")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void removeItem_removesProductFromCart() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(
            new CartItem("prod-1", 2),
            new CartItem("prod-2", 1)
        ));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
        .when()
            .delete("/cart/items/prod-1")
        .then()
            .statusCode(204);

        Cart updated = Cart.findByUserId(USER_ID).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, updated.items.size());
        org.junit.jupiter.api.Assertions.assertEquals("prod-2", updated.items.get(0).getProductId());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void removeItem_returns404WhenProductNotInCart() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
        .when()
            .delete("/cart/items/prod-999")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void clearCart_deletesCartDocument() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
        .when()
            .delete("/cart")
        .then()
            .statusCode(204);

        org.junit.jupiter.api.Assertions.assertTrue(Cart.findByUserId(USER_ID).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void clearCart_returns204EvenWhenNoCart() {
        given()
        .when()
            .delete("/cart")
        .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void getCart_returnsItemsWithPrices() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cart.persist();

        when(priceCacheService.getPrice("prod-1")).thenReturn(25.0);

        given()
        .when()
            .get("/cart")
        .then()
            .statusCode(200)
            .body("items[0].unitPrice", is(25.0f))
            .body("items[0].totalPrice", is(75.0f));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void checkout_createsOrderAndClearsCart() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cart.persist();

        Order mockOrder = new Order();
        mockOrder.id = new ObjectId();
        mockOrder.setUserID(USER_ID);
        mockOrder.setStatus(OrderStatus.INITIATED);
        mockOrder.setPrice(100.0);
        when(orderService.saveOrder(any())).thenReturn(mockOrder);

        given()
        .when()
            .post("/cart/checkout")
        .then()
            .statusCode(201)
            .body("userID", is(USER_ID))
            .body("status", is("INITIATED"));

        assertTrue(Cart.findByUserId(USER_ID).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void checkout_returns404WhenNoCart() {
        given()
        .when()
            .post("/cart/checkout")
        .then()
            .statusCode(404)
            .body("errorCode", is("CART_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void checkout_returns400WhenCartIsEmpty() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>();
        cart.updatedAt = Instant.now();
        cart.persist();

        given()
        .when()
            .post("/cart/checkout")
        .then()
            .statusCode(400)
            .body("errorCode", is("CART_EMPTY"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    void checkout_doesNotClearCartIfOrderFails() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 1)));
        cart.updatedAt = Instant.now();
        cart.persist();

        when(orderService.saveOrder(any())).thenThrow(new RuntimeException("pricing service down"));

        given()
        .when()
            .post("/cart/checkout")
        .then()
            .statusCode(500)
            .body("errorCode", is("INTERNAL_ERROR"));

        assertTrue(Cart.findByUserId(USER_ID).isPresent());
    }
}
