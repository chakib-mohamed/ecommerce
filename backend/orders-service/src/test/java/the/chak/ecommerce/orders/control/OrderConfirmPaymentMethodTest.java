package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import the.chak.ecommerce.orders.control.exceptions.MissingPaymentMethodException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * Where the buyer's payment method reference enters the order, and how far it is allowed to travel.
 *
 * <p>The reference is opaque and single-use, but it is still the credential the charge is made
 * against, so the rule is that it goes exactly one place: onto the order, until the capture that
 * consumes it resolves. It must not reach the sale event, which analytics and anything else
 * downstream consume, and it must not be inferred - an order confirmed without one would reserve
 * stock and then fail at the capture with nothing to charge.
 */
class OrderConfirmPaymentMethodTest {

    private static final String PAYMENT_METHOD = "pm_card_visa";

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);
    private final MongoClient mongoClient = mock(MongoClient.class);
    private final ProductsApiClient productsApiClient = mock(ProductsApiClient.class);

    @SuppressWarnings("unchecked")
    private OrderService service() {
        MongoCollection<Order> orders = mock(MongoCollection.class);
        MongoCollection<OutboxEntry> outbox = mock(MongoCollection.class);
        ClientSession session = mock(ClientSession.class);

        UpdateResult result = mock(UpdateResult.class);
        when(result.getMatchedCount()).thenReturn(1L);
        when(orders.replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class)))
                .thenReturn(result);

        when(orderRepository.mongoCollection()).thenReturn(orders);
        when(outboxRepository.mongoCollection()).thenReturn(outbox);
        when(mongoClient.startSession()).thenReturn(session);
        when(session.withTransaction(any(TransactionBody.class)))
                .thenAnswer(inv -> inv.getArgument(0, TransactionBody.class).execute());
        when(outboxEventFactory.orderInitiated(any(Order.class))).thenReturn(new OutboxEntry());
        when(outboxEventFactory.reserveStock(any(Order.class), any())).thenReturn(new OutboxEntry());

        OrderService service = new OrderService();
        service.orderRepository = orderRepository;
        service.outboxRepository = outboxRepository;
        service.outboxEventFactory = outboxEventFactory;
        service.outboxRelay = outboxRelay;
        service.mongoClient = mongoClient;
        service.productsApiClient = productsApiClient;
        service.meterRegistry = new SimpleMeterRegistry();
        service.stateMachine = new OrderStateMachine();
        service.stepTimeout = java.time.Duration.ofMinutes(5);
        return service;
    }

    @Test
    @DisplayName("Keeps the payment method on the order until the charge is made")
    void confirmOrder_holdsThePaymentMethod() {
        // given - the capture is commanded only once the stock is held, which is well after this
        // call has returned, so the reference has to survive in between
        Order order = initiatedOrder();

        // when
        service().confirmOrder(order.id.toString(), PAYMENT_METHOD);

        // then
        assertEquals(PAYMENT_METHOD, order.getPaymentMethodRef());
    }

    @Test
    @DisplayName("Refuses to confirm an order with no payment method")
    void confirmOrder_withoutAPaymentMethod_isRefused() {
        // given
        Order order = initiatedOrder();

        // when / then - allowed through, this order would reserve stock and only fail at the
        // capture, holding inventory for a charge that was never possible
        assertThrows(MissingPaymentMethodException.class,
                () -> service().confirmOrder(order.id.toString(), null));
    }

    @Test
    @DisplayName("Refuses to confirm an order whose payment method is blank")
    void confirmOrder_withABlankPaymentMethod_isRefused() {
        // given
        Order order = initiatedOrder();

        // when / then
        assertThrows(MissingPaymentMethodException.class,
                () -> service().confirmOrder(order.id.toString(), "   "));
    }

    @Test
    @DisplayName("Leaves the order unconfirmed when the payment method is missing")
    void confirmOrder_withoutAPaymentMethod_doesNotAdvanceTheOrder() {
        // given
        Order order = initiatedOrder();

        // when
        assertThrows(MissingPaymentMethodException.class,
                () -> service().confirmOrder(order.id.toString(), null));

        // then
        assertEquals(OrderStatus.INITIATED, order.getStatus());
    }

    @Test
    @DisplayName("Keeps the payment method out of the published sale")
    void confirmOrder_doesNotPublishThePaymentMethod() {
        // given - order-initiated goes to analytics and anything else that subscribes; a payment
        // credential on that topic is readable by every consumer and retained by the broker
        Order order = initiatedOrder();

        // when
        service().confirmOrder(order.id.toString(), PAYMENT_METHOD);

        // then
        OutboxEventFactory realFactory = new OutboxEventFactory();
        realFactory.jsonb = jakarta.json.bind.JsonbBuilder.create();
        String published = realFactory.orderInitiated(order).payload;
        assertFalse(published.contains(PAYMENT_METHOD),
                "the sale event must not carry the payment method: " + published);
    }

    // -- helpers ------------------------------------------------------------

    private Order initiatedOrder() {
        Order order = new Order();
        order.id = new ObjectId();
        order.setStatus(OrderStatus.INITIATED);
        order.setUserID("owner");
        order.setProducts(new ArrayList<>());
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);
        return order;
    }
}
