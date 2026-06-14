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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    // A real registry so checkout outcome counters are recorded and assertable.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // --addItem ------------------------------------------------------------

    @Test
    @DisplayName("Creates a new cart holding the single added item when the user has no cart yet")
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
    @DisplayName("Increments the quantity instead of adding a duplicate line when the product is already in the cart")
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
    @DisplayName("Adds a new line when the product is not yet in an existing cart")
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

    // --getCart ------------------------------------------------------------

    @Test
    @DisplayName("Returns empty when the user has no cart")
    void getCart_noCart_returnsEmpty() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertTrue(cartService.getCart("no-such-user").isEmpty());
    }

    @Test
    @DisplayName("Returns the cart response when the user has a cart")
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

    // --updateItem ---------------------------------------------------------

    @Test
    @DisplayName("Returns empty when updating an item for a user with no cart")
    void updateItem_cartNotFound_returnsEmpty() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertTrue(cartService.updateItem("no-such-user", "prod-1", new UpdateItemRequest()).isEmpty());
    }

    @Test
    @DisplayName("Returns empty when updating a product that is not in the cart")
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
    @DisplayName("Updates the line quantity when the product is in the cart")
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

    // --removeItem ---------------------------------------------------------

    @Test
    @DisplayName("Returns false when removing an item for a user with no cart")
    void removeItem_cartNotFound_returnsFalse() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertFalse(cartService.removeItem("no-such-user", "prod-1"));
    }

    @Test
    @DisplayName("Returns false when removing a product that is not in the cart")
    void removeItem_itemNotFound_returnsFalse() {
        // given
        Cart cart = new Cart();
        cart.items.add(new CartItem("prod-1", 1));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertFalse(cartService.removeItem(USER_ID, "prod-x"));
    }

    @Test
    @DisplayName("Removes the line and returns true when the product is in the cart")
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

    // --clearCart ----------------------------------------------------------

    @Test
    @DisplayName("Returns false when clearing a cart for a user with no cart")
    void clearCart_noCart_returnsFalse() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertFalse(cartService.clearCart("no-such-user"));
    }

    @Test
    @DisplayName("Deletes the cart and returns true when the user has a cart")
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

    // --checkout -----------------------------------------------------------

    @Test
    @DisplayName("Throws CartNotFoundException when checking out a user with no cart")
    void checkout_noCart_throwsCartNotFoundException() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertThrows(CartNotFoundException.class, () -> cartService.checkout("no-such-user"));
    }

    @Test
    @DisplayName("Records a failed checkout outcome when the cart is not found")
    void checkout_noCart_recordsFailureOutcome() {
        // given
        when(cartRepository.findByUserId(anyString())).thenReturn(Optional.empty());

        // when & then
        assertThrows(CartNotFoundException.class, () -> cartService.checkout("no-such-user"));
        assertEquals(1.0, meterRegistry.get("checkouts").tag("outcome", "failure").counter().count(), 0.001);
    }

    @Test
    @DisplayName("Throws CartEmptyException when checking out a cart with no items")
    void checkout_emptyCart_throwsCartEmptyException() {
        // given
        Cart cart = new Cart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertThrows(CartEmptyException.class, () -> cartService.checkout(USER_ID));
    }

    @Test
    @DisplayName("Records a failed checkout outcome when the cart is empty")
    void checkout_emptyCart_recordsFailureOutcome() {
        // given
        Cart cart = new Cart();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertThrows(CartEmptyException.class, () -> cartService.checkout(USER_ID));
        assertEquals(1.0, meterRegistry.get("checkouts").tag("outcome", "failure").counter().count(), 0.001);
    }

    @Test
    @DisplayName("Creates the order, delegates to the order service, and deletes the cart on valid checkout")
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

    @Test
    @DisplayName("Records a successful checkout outcome on valid checkout")
    void checkout_validCart_recordsSuccessOutcome() {
        // given
        Cart cart = new Cart();
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        // when
        cartService.checkout(USER_ID);

        // then
        assertEquals(1.0, meterRegistry.get("checkouts").tag("outcome", "success").counter().count(), 0.001);
    }
}
