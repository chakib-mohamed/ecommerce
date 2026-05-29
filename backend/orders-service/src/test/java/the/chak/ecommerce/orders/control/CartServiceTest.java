package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.orders.boundary.dto.AddItemRequest;
import the.chak.ecommerce.orders.boundary.dto.CartResponse;
import the.chak.ecommerce.orders.boundary.dto.UpdateItemRequest;
import the.chak.ecommerce.orders.control.exceptions.CartEmptyException;
import the.chak.ecommerce.orders.control.exceptions.CartNotFoundException;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.repository.CartRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    static final String USER_ID = "user-cart-test";

    @InjectMocks
    CartService cartService;

    @Mock
    PriceCacheService priceCacheService;

    @Mock
    OrderService orderService;

    @Mock
    CartRepository cartRepository;

    // --addItem ────────────────────────────────────────────────────────────

    @Test
    void addItem_noExistingCart_createsCartWithSingleItem() {
        // given
        AddItemRequest request = new AddItemRequest();
        request.setProductId("prod-1");
        request.setQuantity(2);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        // when
        CartResponse response = cartService.addItem(USER_ID, request);

        // then
        assertEquals(USER_ID, response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals("prod-1", response.getItems().get(0).getProductId());
        assertEquals(2, response.getItems().get(0).getQuantity());
        verify(cartRepository).persistOrUpdate(any(Cart.class));
    }

    @Test
    void addItem_existingCartSameProduct_incrementsQuantity() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items.add(new CartItem("prod-1", 3));

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        AddItemRequest request = new AddItemRequest();
        request.setProductId("prod-1");
        request.setQuantity(2);

        // when
        CartResponse response = cartService.addItem(USER_ID, request);

        // then
        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().get(0).getQuantity());
    }

    @Test
    void addItem_existingCartDifferentProduct_addsNewItem() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items.add(new CartItem("prod-1", 1));

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        AddItemRequest request = new AddItemRequest();
        request.setProductId("prod-2");
        request.setQuantity(1);

        // when
        CartResponse response = cartService.addItem(USER_ID, request);

        // then
        assertEquals(2, response.getItems().size());
    }

    // --getCart ────────────────────────────────────────────────────────────

    @Test
    void getCart_noCart_returnsEmpty() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertTrue(cartService.getCart("no-such-user").isEmpty());
    }

    @Test
    void getCart_existingCart_returnsResponse() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when
        var result = cartService.getCart(USER_ID);

        // then
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getUserId());
    }

    // --updateItem ─────────────────────────────────────────────────────────

    @Test
    void updateItem_cartNotFound_returnsEmpty() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertTrue(cartService.updateItem("no-such-user", "prod-1", new UpdateItemRequest()).isEmpty());
    }

    @Test
    void updateItem_itemNotFound_returnsEmpty() {
        // given
        Cart cart = new Cart();
        cart.items.add(new CartItem("prod-1", 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        UpdateItemRequest request = new UpdateItemRequest();
        request.setQuantity(5);

        // when & then
        assertTrue(cartService.updateItem(USER_ID, "prod-x", request).isEmpty());
    }

    @Test
    void updateItem_itemExists_updatesQuantity() {
        // given
        Cart cart = new Cart();
        cart.items.add(new CartItem("prod-1", 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        UpdateItemRequest request = new UpdateItemRequest();
        request.setQuantity(7);

        // when
        var result = cartService.updateItem(USER_ID, "prod-1", request);

        // then
        assertTrue(result.isPresent());
        assertEquals(7, result.get().getItems().get(0).getQuantity());
    }

    // --removeItem ─────────────────────────────────────────────────────────

    @Test
    void removeItem_cartNotFound_returnsFalse() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertFalse(cartService.removeItem("no-such-user", "prod-1"));
    }

    @Test
    void removeItem_itemNotFound_returnsFalse() {
        // given
        Cart cart = new Cart();
        cart.items.add(new CartItem("prod-1", 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertFalse(cartService.removeItem(USER_ID, "prod-x"));
    }

    @Test
    void removeItem_itemExists_removesItem() {
        // given
        Cart cart = new Cart();
        cart.items.add(new CartItem("prod-1", 3));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when
        boolean removed = cartService.removeItem(USER_ID, "prod-1");

        // then
        assertTrue(removed);
        assertTrue(cart.items.isEmpty());
    }

    // --clearCart ──────────────────────────────────────────────────────────

    @Test
    void clearCart_noCart_returnsFalse() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertFalse(cartService.clearCart("no-such-user"));
    }

    @Test
    void clearCart_existingCart_deletesAndReturnsTrue() {
        // given
        Cart cart = new Cart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when
        boolean cleared = cartService.clearCart(USER_ID);

        // then
        assertTrue(cleared);
        verify(cartRepository).delete(cart);
    }

    // --checkout ───────────────────────────────────────────────────────────

    @Test
    void checkout_noCart_throwsCartNotFoundException() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertThrows(CartNotFoundException.class, () -> cartService.checkout("no-such-user"));
    }

    @Test
    void checkout_emptyCart_throwsCartEmptyException() {
        // given
        Cart cart = new Cart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertThrows(CartEmptyException.class, () -> cartService.checkout(USER_ID));
    }

    @Test
    void checkout_validCart_delegatesToOrderServiceAndDeleteCart() {
        // given
        Cart cart = new Cart();
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when
        Order order = cartService.checkout(USER_ID);

        // then
        assertNotNull(order);
        assertEquals(USER_ID, order.getUserID());
        assertEquals(1, order.getProducts().size());
        verify(orderService).saveOrder(any(Order.class));
        verify(cartRepository).delete(cart);
    }
}
