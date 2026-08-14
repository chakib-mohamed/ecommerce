package the.chak.ecommerce.orders.control;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.ProductVO;
import the.chak.ecommerce.orders.repository.CartRepository;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * The half of checkout atomicity that a mocked Mongo cannot answer: that the paired writes really do
 * commit and roll back together against a live server, and that inserting through the session still
 * assigns the order its id the way {@code persist} does.
 *
 * <p>{@link CartCheckoutAtomicityTest} covers the calling code - that both writes are handed one
 * session. This covers the server's half of the bargain.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class CheckoutTransactionTest {

    @Inject
    OrderService orderService;

    @Inject
    OrderRepository orderRepository;

    @Inject
    CartRepository cartRepository;

    @Inject
    MongoClient mongoClient;

    @Test
    @DisplayName("Assigns the order an id when it is inserted through a session")
    void insertOrder_assignsTheGeneratedId() {
        // given - persist() fills the id in; the session insert has to do the same, or every
        // caller downstream is holding an order it cannot address
        Order order = pricedOrder();

        // when
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                orderService.insertOrder(order, session);
                return null;
            });
        }

        // then
        assertNotNull(order.getId(), "insertOrder must populate the generated id");
        Order reloaded = orderRepository.findById(order.id);
        assertNotNull(reloaded);
        assertEquals(OrderStatus.INITIATED, reloaded.getStatus());
    }

    @Test
    @DisplayName("Commits the order and the cart removal together")
    void bothWrites_commitTogether() {
        // given
        Cart cart = persistedCart("buyer-commit");
        Order order = pricedOrder();

        // when
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                orderService.insertOrder(order, session);
                cartRepository.mongoCollection()
                        .deleteOne(session, com.mongodb.client.model.Filters.eq("_id", cart.id));
                return null;
            });
        }

        // then
        assertNotNull(orderRepository.findById(order.id));
        assertTrue(cartRepository.findByUserId("buyer-commit").isEmpty());
    }

    @Test
    @DisplayName("Leaves no order behind when the cart removal fails")
    void abortedTransaction_leavesNoOrder() {
        // given - this is the crash-in-the-middle case: without a transaction the order would
        // survive alongside a live cart, and the buyer's retry would order everything twice
        Cart cart = persistedCart("buyer-abort");
        Order order = pricedOrder();

        // when
        assertThrows(IllegalStateException.class, () -> {
            try (ClientSession session = mongoClient.startSession()) {
                session.withTransaction(() -> {
                    orderService.insertOrder(order, session);
                    cartRepository.mongoCollection()
                            .deleteOne(session, com.mongodb.client.model.Filters.eq("_id", cart.id));
                    throw new IllegalStateException("failure after both writes");
                });
            }
        });

        // then - neither write survived
        ObjectId written = order.id;
        assertTrue(written == null || orderRepository.findById(written) == null,
                "the aborted insert must not be visible");
        assertTrue(cartRepository.findByUserId("buyer-abort").isPresent(),
                "the cart must survive an aborted checkout");
    }

    private Cart persistedCart(String userId) {
        Cart cart = new Cart();
        cart.userId = userId;
        cart.items = new ArrayList<>(List.of(new CartItem("prod-1", 1)));
        cartRepository.persist(cart);
        return cart;
    }

    private static Order pricedOrder() {
        ProductVO line = new ProductVO();
        line.setProductID("prod-1");
        line.setQty(1);
        line.setPrice(BigDecimal.valueOf(10.0));

        Order order = new Order();
        order.setUserID("buyer");
        order.setStatus(OrderStatus.INITIATED);
        order.setPrice(BigDecimal.valueOf(10.0));
        order.setProducts(new ArrayList<>(List.of(line)));
        return order;
    }
}
