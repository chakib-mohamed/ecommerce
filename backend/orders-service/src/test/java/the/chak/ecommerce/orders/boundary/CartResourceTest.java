package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.control.OrderService;
import the.chak.ecommerce.orders.control.PriceCacheService;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.repository.CartRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
class CartResourceTest {

    static final String USER_ID = "test-user-123";

    @InjectMock
    PriceCacheService priceCacheService;

    @InjectMock
    OrderService orderService;

    @Inject
    CartRepository cartRepository;

    @BeforeEach
    void cleanup() {
        cartRepository.deleteAll();
        when(priceCacheService.getPrice(anyString())).thenReturn(null);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 201 with a new cart containing the added item when the user has no cart")
    void addItem_noExistingCart_createsCartWithItem() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"product_id\": \"prod-1\", \"quantity\": 2 }")
                .when().post("/cart/items");

        // then
        response.then().statusCode(201)
                .body("user_id", is(USER_ID))
                .body("items", hasSize(1))
                .body("items[0].product_id", is("prod-1"))
                .body("items[0].quantity", is(2));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Increments the existing line quantity when adding a product already in the cart")
    void addItem_existingProduct_incrementsQuantity() {
        // given
        given().contentType(ContentType.JSON)
                .body("{ \"product_id\": \"prod-1\", \"quantity\": 2 }")
                .when().post("/cart/items");

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"product_id\": \"prod-1\", \"quantity\": 3 }")
                .when().post("/cart/items");

        // then
        response.then().statusCode(201)
                .body("items", hasSize(1))
                .body("items[0].quantity", is(5));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 400 with VALIDATION_ERROR when adding an item with a non-positive quantity")
    void addItem_zeroOrNegativeQuantity_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"product_id\": \"prod-1\", \"quantity\": 0 }")
                .when().post("/cart/items");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 400 with VALIDATION_ERROR when adding an item with a blank product id")
    void addItem_blankProductId_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"product_id\": \"\", \"quantity\": 1 }")
                .when().post("/cart/items");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 400 with VALIDATION_ERROR when updating an item to a quantity of zero")
    void updateItem_zeroQuantity_returns400() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"quantity\": 0 }")
                .when().put("/cart/items/prod-1");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 404 when fetching a cart for a user who has none")
    void getCart_noExistingCart_returns404() {
        // when
        var response = given().when().get("/cart");

        // then
        response.then().statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 200 with the cart items when the user has a cart")
    void getCart_existingCart_returnsItems() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().when().get("/cart");

        // then
        response.then().statusCode(200)
                .body("user_id", is(USER_ID))
                .body("items", hasSize(1))
                .body("items[0].product_id", is("prod-1"))
                .body("items[0].quantity", is(3));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 200 with the updated quantity when updating a product in the cart")
    void updateItem_existingProduct_updatesQuantity() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"quantity\": 10 }")
                .when().put("/cart/items/prod-1");

        // then
        response.then().statusCode(200).body("items[0].quantity", is(10));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 404 when updating a product that is not in the cart")
    void updateItem_unknownProduct_returns404() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().contentType(ContentType.JSON)
                .body("{ \"quantity\": 5 }")
                .when().put("/cart/items/prod-999");

        // then
        response.then().statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 204 and removes the line when deleting a product in the cart")
    void removeItem_existingProduct_removesFromCart() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(
                new CartItem("prod-1", 2),
                new CartItem("prod-2", 1)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().when().delete("/cart/items/prod-1");

        // then
        response.then().statusCode(204);
        Cart updated = cartRepository.findByUserId(USER_ID).orElseThrow();
        assertEquals(1, updated.items.size());
        assertEquals("prod-2", updated.items.get(0).getProductId());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 404 when removing a product that is not in the cart")
    void removeItem_unknownProduct_returns404() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().when().delete("/cart/items/prod-999");

        // then
        response.then().statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 204 and deletes the cart when clearing an existing cart")
    void clearCart_existingCart_deletesDocument() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().when().delete("/cart");

        // then
        response.then().statusCode(204);
        assertTrue(cartRepository.findByUserId(USER_ID).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 204 when clearing a cart for a user who has none")
    void clearCart_noExistingCart_returns204() {
        // when
        var response = given().when().delete("/cart");

        // then
        response.then().statusCode(204);
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Includes unit and total price in the cart response when cached prices are available")
    void getCart_withCachedPrices_includesPriceData() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 3)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);
        when(priceCacheService.getPrice("prod-1")).thenReturn(25.0);

        // when
        var response = given().when().get("/cart");

        // then
        response.then().statusCode(200)
                .body("items[0].unit_price", is(25.0f))
                .body("items[0].total_price", is(75.0f));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 201 with the initiated order and clears the cart on valid checkout")
    void checkout_validCart_createsOrderAndClearsCart() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setStatus(OrderStatus.INITIATED);
            o.setPrice(100.0);
            return o;
        }).when(orderService).saveOrder(any());

        // when
        var response = given().when().post("/cart/checkout");

        // then
        response.then().statusCode(201)
                .body("user_id", is(USER_ID))
                .body("status", is("INITIATED"));
        assertTrue(cartRepository.findByUserId(USER_ID).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 404 with CART_NOT_FOUND when checking out without a cart")
    void checkout_noExistingCart_returns404() {
        // when
        var response = given().when().post("/cart/checkout");

        // then
        response.then().statusCode(404).body("type", is("FUNCTIONAL")).body("error_code", is("CART_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 400 with CART_EMPTY when checking out a cart with no items")
    void checkout_emptyCart_returns400() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>();
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);

        // when
        var response = given().when().post("/cart/checkout");

        // then
        response.then().statusCode(400).body("type", is("FUNCTIONAL")).body("error_code", is("CART_EMPTY"));
    }

    @Test
    @TestSecurity(user = USER_ID)
    @JwtSecurity(claims = { @Claim(key = "sub", value = USER_ID) })
    @DisplayName("Returns 500 and preserves the cart when order creation fails during checkout")
    void checkout_orderServiceFails_preservesCart() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 1)));
        cart.updatedAt = Instant.now();
        cartRepository.persist(cart);
        doThrow(new RuntimeException("pricing service down")).when(orderService).saveOrder(any());

        // when
        var response = given().when().post("/cart/checkout");

        // then
        response.then().statusCode(500).body("type", is("TECHNICAL")).body("error_code", is("INTERNAL_ERROR"));
        assertTrue(cartRepository.findByUserId(USER_ID).isPresent());
    }
}
