package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.TransactionBody;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.repository.CartRepository;

/**
 * Checkout writes to two collections: it inserts the order and removes the cart it was built from.
 * Done as two independent writes, a crash in between leaves the buyer holding both an order and the
 * cart that produced it, and the obvious retry buys the same goods twice.
 *
 * <p>The two writes therefore share one transaction. Pricing does not: it is REST traffic to two
 * other services, and persistence-conventions.md forbids network I/O inside a transaction, so the
 * order is priced before the transaction opens.
 *
 * <p>Mongo is mocked rather than containerised for the same reason as
 * {@link OrderConfirmConcurrencyTest} - what is under test is that both writes are handed the same
 * session, which is a property of the calling code, not of the server.
 */
class CartCheckoutAtomicityTest {

    private static final String USER_ID = "buyer-1";

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final MongoClient mongoClient = mock(MongoClient.class);
    private final ClientSession session = mock(ClientSession.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @SuppressWarnings("unchecked")
    private final MongoCollection<Cart> carts = mock(MongoCollection.class);

    @SuppressWarnings("unchecked")
    private CartService service() {
        when(mongoClient.startSession()).thenReturn(session);
        // Run the transaction body inline so the two writes actually execute against the mocks.
        when(session.withTransaction(any(TransactionBody.class)))
                .thenAnswer(inv -> inv.getArgument(0, TransactionBody.class).execute());
        when(cartRepository.mongoCollection()).thenReturn(carts);

        CartService cartService = new CartService();
        cartService.cartRepository = cartRepository;
        cartService.orderService = orderService;
        cartService.mongoClient = mongoClient;
        cartService.meterRegistry = meterRegistry;
        return cartService;
    }

    private Cart cartWithOneItem() {
        Cart cart = new Cart();
        cart.userId = USER_ID;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 2)));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        return cart;
    }

    @Test
    @DisplayName("Inserts the order and removes the cart on one and the same session")
    void bothWrites_shareOneSession() {
        // given
        cartWithOneItem();
        CartService cartService = service();

        // when
        cartService.checkout(USER_ID);

        // then - a shared session is what makes the pair atomic; two sessions would not be
        ArgumentCaptor<ClientSession> insertSession = ArgumentCaptor.forClass(ClientSession.class);
        verify(orderService).insertOrder(any(Order.class), insertSession.capture());
        ArgumentCaptor<ClientSession> deleteSession = ArgumentCaptor.forClass(ClientSession.class);
        verify(carts).deleteOne(deleteSession.capture(), any(Bson.class));

        assertSame(session, insertSession.getValue());
        assertSame(insertSession.getValue(), deleteSession.getValue());
    }

    @Test
    @DisplayName("Prices the order before the transaction is opened")
    void pricing_happensOutsideTheTransaction() {
        // given
        cartWithOneItem();
        CartService cartService = service();

        // when
        cartService.checkout(USER_ID);

        // then - pricing calls two other services over REST; inside a transaction it would hold
        // the write locks open for the duration of a network round trip
        InOrder order = inOrder(orderService, mongoClient);
        order.verify(orderService).priceOrder(any(Order.class));
        order.verify(mongoClient).startSession();
    }

    @Test
    @DisplayName("Propagates the failure and records no success when the cart cannot be removed")
    void cartDeleteFails_checkoutFails() {
        // given
        cartWithOneItem();
        CartService cartService = service();
        when(carts.deleteOne(any(ClientSession.class), any(Bson.class)))
                .thenThrow(new IllegalStateException("write conflict"));

        // when / then - the insert is inside the aborted transaction, so no order survives either
        assertThrows(IllegalStateException.class, () -> cartService.checkout(USER_ID));
        assertNull(meterRegistry.find("checkouts").tag("outcome", "success").counter());
    }

    @Test
    @DisplayName("Counts the order as created only once the transaction has committed")
    void orderCreatedMetric_isRecordedAfterTheCommit() {
        // given
        cartWithOneItem();
        CartService cartService = service();

        // when
        cartService.checkout(USER_ID);

        // then - recording inside the body would count orders that the abort rolled back
        InOrder order = inOrder(session, orderService);
        order.verify(session).withTransaction(any(TransactionBody.class));
        order.verify(orderService).recordOrderCreated(any(Order.class));
    }

    @Test
    @DisplayName("Opens no transaction when the cart is empty")
    void emptyCart_neverReachesMongo() {
        // given
        Cart cart = new Cart();
        cart.userId = USER_ID;
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        CartService cartService = new CartService();
        cartService.cartRepository = cartRepository;
        cartService.orderService = orderService;
        cartService.mongoClient = mongoClient;
        cartService.meterRegistry = meterRegistry;

        // when / then
        assertThrows(the.chak.ecommerce.orders.control.exceptions.CartEmptyException.class,
                () -> cartService.checkout(USER_ID));
        verify(mongoClient, never()).startSession();
    }
}
