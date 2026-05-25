package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.boundary.dto.AddItemRequest;
import the.chak.ecommerce.orders.boundary.dto.CartResponse;
import the.chak.ecommerce.orders.boundary.dto.UpdateItemRequest;
import the.chak.ecommerce.orders.control.exceptions.CartEmptyException;
import the.chak.ecommerce.orders.control.exceptions.CartNotFoundException;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class CartServiceTest {

    static final String USER_ID = "user-cart-test";

    @Inject
    CartService cartService;

    @InjectMock
    PriceCacheService priceCacheService;

    @InjectMock
    OrderService orderService;

    @BeforeEach
    void cleanup() {
        Cart.deleteAll();
        when(priceCacheService.getPrice(any())).thenReturn(null);
    }

    // ── addItem ────────────────────────────────────────────────────────────

    @Test
    void addItem_noExistingCart_createsCartWithSingleItem() {
        // given
        AddItemRequest request = new AddItemRequest();
        request.setProductId("prod-1");
        request.setQuantity(2);

        // when
        CartResponse response = cartService.addItem(USER_ID, request);

        // then
        assertEquals(USER_ID, response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals("prod-1", response.getItems().get(0).getProductId());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void addItem_existingCartSameProduct_incrementsQuantity() {
        // given — seed a cart with prod-1 qty 3
        AddItemRequest first = new AddItemRequest();
        first.setProductId("prod-1");
        first.setQuantity(3);
        cartService.addItem(USER_ID, first);

        AddItemRequest second = new AddItemRequest();
        second.setProductId("prod-1");
        second.setQuantity(2);

        // when
        CartResponse response = cartService.addItem(USER_ID, second);

        // then
        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().get(0).getQuantity());
    }

    @Test
    void addItem_existingCartDifferentProduct_addsNewItem() {
        // given
        AddItemRequest first = new AddItemRequest();
        first.setProductId("prod-1");
        first.setQuantity(1);
        cartService.addItem(USER_ID, first);

        AddItemRequest second = new AddItemRequest();
        second.setProductId("prod-2");
        second.setQuantity(1);

        // when
        CartResponse response = cartService.addItem(USER_ID, second);

        // then
        assertEquals(2, response.getItems().size());
    }

    // ── getCart ────────────────────────────────────────────────────────────

    @Test
    void getCart_noCart_returnsEmpty() {
        assertTrue(cartService.getCart("no-such-user").isEmpty());
    }

    @Test
    void getCart_existingCart_returnsResponse() {
        // given
        AddItemRequest request = new AddItemRequest();
        request.setProductId("prod-1");
        request.setQuantity(1);
        cartService.addItem(USER_ID, request);

        // when
        var result = cartService.getCart(USER_ID);

        // then
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getUserId());
    }

    // ── updateItem ─────────────────────────────────────────────────────────

    @Test
    void updateItem_cartNotFound_returnsEmpty() {
        UpdateItemRequest request = new UpdateItemRequest();
        request.setQuantity(5);

        assertTrue(cartService.updateItem("no-such-user", "prod-1", request).isEmpty());
    }

    @Test
    void updateItem_itemNotFound_returnsEmpty() {
        // given — cart exists but doesn't have prod-x
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(1);
        cartService.addItem(USER_ID, add);

        UpdateItemRequest request = new UpdateItemRequest();
        request.setQuantity(5);

        // when / then
        assertTrue(cartService.updateItem(USER_ID, "prod-x", request).isEmpty());
    }

    @Test
    void updateItem_itemExists_updatesQuantity() {
        // given
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(1);
        cartService.addItem(USER_ID, add);

        UpdateItemRequest request = new UpdateItemRequest();
        request.setQuantity(7);

        // when
        var result = cartService.updateItem(USER_ID, "prod-1", request);

        // then
        assertTrue(result.isPresent());
        assertEquals(7, result.get().getItems().get(0).getQuantity());
    }

    // ── removeItem ─────────────────────────────────────────────────────────

    @Test
    void removeItem_cartNotFound_returnsFalse() {
        assertFalse(cartService.removeItem("no-such-user", "prod-1"));
    }

    @Test
    void removeItem_itemNotFound_returnsFalse() {
        // given — cart exists without the target product
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(1);
        cartService.addItem(USER_ID, add);

        // when / then
        assertFalse(cartService.removeItem(USER_ID, "prod-x"));
    }

    @Test
    void removeItem_itemExists_removesItemAndUpdatesTimestamp() {
        // given
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(3);
        cartService.addItem(USER_ID, add);

        // when
        boolean removed = cartService.removeItem(USER_ID, "prod-1");

        // then
        assertTrue(removed);
        assertTrue(cartService.getCart(USER_ID).map(r -> r.getItems().isEmpty()).orElse(false));
    }

    // ── clearCart ──────────────────────────────────────────────────────────

    @Test
    void clearCart_noCart_returnsFalse() {
        assertFalse(cartService.clearCart("no-such-user"));
    }

    @Test
    void clearCart_existingCart_deletesAndReturnsTrue() {
        // given
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(1);
        cartService.addItem(USER_ID, add);

        // when
        boolean cleared = cartService.clearCart(USER_ID);

        // then
        assertTrue(cleared);
        assertTrue(cartService.getCart(USER_ID).isEmpty());
    }

    // ── checkout ───────────────────────────────────────────────────────────

    @Test
    void checkout_noCart_throwsCartNotFoundException() {
        assertThrows(CartNotFoundException.class, () -> cartService.checkout("no-such-user"));
    }

    @Test
    void checkout_emptyCart_throwsCartEmptyException() {
        // given — cart exists but has no items
        Cart empty = new Cart();
        empty.userId = USER_ID;
        empty.persist();

        // when / then
        assertThrows(CartEmptyException.class, () -> cartService.checkout(USER_ID));
    }

    @Test
    void checkout_validCart_delegatesToOrderServiceAndDeletesCart() {
        // given
        AddItemRequest add = new AddItemRequest();
        add.setProductId("prod-1");
        add.setQuantity(2);
        cartService.addItem(USER_ID, add);

        // capture and persist the order passed to saveOrder so checkout can return it
        doAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.persist();
            return order;
        }).when(orderService).saveOrder(any(Order.class));

        // when
        Order order = cartService.checkout(USER_ID);

        // then
        assertNotNull(order);
        assertEquals(USER_ID, order.getUserID());
        assertEquals(1, order.getProducts().size());
        assertEquals("prod-1", order.getProducts().get(0).getProductID());
        assertEquals(2, order.getProducts().get(0).getQty());
        verify(orderService).saveOrder(any(Order.class));
        assertTrue(cartService.getCart(USER_ID).isEmpty()); // cart deleted
    }
}
