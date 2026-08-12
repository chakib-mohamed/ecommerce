package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.TransactionBody;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.control.exceptions.ConcurrentOrderModificationException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * The optimistic-locking half of {@code confirmOrder}, which the status guard alone cannot provide:
 * that guard is a read-then-write, so two confirmations arriving together would both pass it. The
 * write is conditional on the version read, and the transaction that commits second matches nothing.
 *
 * <p>Mongo is mocked here rather than containerised because the case being exercised is a losing
 * conditional write, which a single-threaded integration test cannot produce on demand.
 */
class OrderConfirmConcurrencyTest {

    /** Opaque single-use reference; confirming requires one, and its value is never meaningful. */
    private static final String PAYMENT_METHOD = "pm_card_visa";

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);
    private final MongoClient mongoClient = mock(MongoClient.class);

    @SuppressWarnings("unchecked")
    private OrderService serviceWriting(long matchedCount) {
        MongoCollection<Order> orders = mock(MongoCollection.class);
        MongoCollection<OutboxEntry> outbox = mock(MongoCollection.class);
        ClientSession session = mock(ClientSession.class);

        UpdateResult result = mock(UpdateResult.class);
        when(result.getMatchedCount()).thenReturn(matchedCount);
        when(orders.replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class)))
                .thenReturn(result);

        when(orderRepository.mongoCollection()).thenReturn(orders);
        when(outboxRepository.mongoCollection()).thenReturn(outbox);
        when(mongoClient.startSession()).thenReturn(session);
        // Run the transaction body inline so the conditional write actually executes.
        when(session.withTransaction(any(TransactionBody.class)))
                .thenAnswer(inv -> inv.getArgument(0, TransactionBody.class).execute());

        OrderService service = new OrderService();
        service.orderRepository = orderRepository;
        service.outboxRepository = outboxRepository;
        service.outboxEventFactory = outboxEventFactory;
        service.outboxRelay = outboxRelay;
        service.mongoClient = mongoClient;
        service.meterRegistry = new SimpleMeterRegistry();
        service.stateMachine = new OrderStateMachine();
        service.stepTimeout = java.time.Duration.ofMinutes(5);
        return service;
    }

    private Order initiatedOrder(Long version) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("owner");
        order.setProducts(new ArrayList<>());
        order.setVersion(version);
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);
        return order;
    }

    @Test
    @DisplayName("Rejects a confirmation whose conditional write matched no order")
    void confirmOrder_versionMoved_isRejected() {
        // given - the write matches nothing, as it would for the second of two racing confirmations
        OrderService service = serviceWriting(0);
        Order order = initiatedOrder(null);

        // when / then
        assertThrows(ConcurrentOrderModificationException.class,
                () -> service.confirmOrder(order.id.toString(), PAYMENT_METHOD));
    }

    @Test
    @DisplayName("Advances the version of an order that already carries one")
    void confirmOrder_existingVersion_isIncremented() {
        // given
        OrderService service = serviceWriting(1);
        Order order = initiatedOrder(5L);

        // when
        Order confirmed = service.confirmOrder(order.id.toString(), PAYMENT_METHOD);

        // then
        assertEquals(6L, confirmed.getVersion());
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    @DisplayName("Starts the version at one for an order written before versions existed")
    void confirmOrder_noVersion_startsAtOne() {
        // given
        OrderService service = serviceWriting(1);
        Order order = initiatedOrder(null);

        // when
        Order confirmed = service.confirmOrder(order.id.toString(), PAYMENT_METHOD);

        // then
        assertEquals(1L, confirmed.getVersion());
    }
}
