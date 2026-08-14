package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * Which replies the saga acts on, and which it throws away.
 *
 * <p>Replies arrive at-least-once and can arrive late, so acting on the wrong one is the failure
 * that matters: a stale stock-reserved would advance an order the sweep had already cancelled and
 * compensated, leaving it RESERVED against stock that had been given back.
 */
class SagaServiceTest {

    private static final String ORDER_ID = new ObjectId().toString();
    private static final String STEP_ID = "step-1";

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);
    private final MongoClient mongoClient = mock(MongoClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @SuppressWarnings("unchecked")
    private final MongoCollection<Order> orders = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<OutboxEntry> outbox = mock(MongoCollection.class);

    @SuppressWarnings("unchecked")
    private SagaService service(long matchedCount) {
        ClientSession session = mock(ClientSession.class);
        UpdateResult result = mock(UpdateResult.class);
        when(result.getMatchedCount()).thenReturn(matchedCount);
        when(orders.replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class)))
                .thenReturn(result);
        when(orderRepository.mongoCollection()).thenReturn(orders);
        when(outboxRepository.mongoCollection()).thenReturn(outbox);
        when(mongoClient.startSession()).thenReturn(session);
        when(session.withTransaction(any(TransactionBody.class)))
                .thenAnswer(inv -> inv.getArgument(0, TransactionBody.class).execute());
        when(outboxEventFactory.orderCancelled(any(Order.class), any()))
                .thenReturn(new OutboxEntry());
        when(outboxEventFactory.capturePayment(any(Order.class), any(), any()))
                .thenReturn(new OutboxEntry());

        SagaService saga = new SagaService();
        saga.stepTimeout = java.time.Duration.ofMinutes(5);
        saga.orderRepository = orderRepository;
        saga.outboxRepository = outboxRepository;
        saga.outboxEventFactory = outboxEventFactory;
        saga.outboxRelay = outboxRelay;
        saga.mongoClient = mongoClient;
        saga.stateMachine = new OrderStateMachine();
        saga.meterRegistry = meterRegistry;
        return saga;
    }

    @Test
    @DisplayName("Advances a confirmed order to reserved when the catalog says it holds the stock")
    void stockReserved_advancesTheOrder() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then - what the reply then opens is SagaPaymentStepTest's subject; here it is only that
        // the order moved
        assertEquals(OrderStatus.RESERVED, order.getStatus());
    }

    @Test
    @DisplayName("Cancels the order when the catalog cannot meet it")
    void stockRejected_cancelsTheOrder() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockRejected(ORDER_ID, STEP_ID, "INSUFFICIENT_STOCK");

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(outboxEventFactory).orderCancelled(any(Order.class), any());
    }

    @Test
    @DisplayName("Issues no compensation when the reservation was refused")
    void stockRejected_doesNotReleaseStock() {
        // given - the reservation is all-or-nothing, so a refusal took nothing
        orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockRejected(ORDER_ID, STEP_ID, "INSUFFICIENT_STOCK");

        // then
        verify(outboxEventFactory, never()).releaseStock(any(), any());
    }

    @Test
    @DisplayName("Falls back to a stated reason when the reply gives none")
    void stockRejected_withoutAReason_stillRecordsOne() {
        // given - the cancellation is what a buyer is shown; "no reason given" is not usable
        orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockRejected(ORDER_ID, STEP_ID, null);

        // then
        verify(outboxEventFactory).orderCancelled(any(Order.class),
                org.mockito.ArgumentMatchers.eq(SagaService.REASON_OUT_OF_STOCK));
    }

    @Test
    @DisplayName("Discards a rejection naming a step the order is no longer waiting for")
    void rejectionForAnOldStep_isDiscarded() {
        // given - the same guard has to hold on the rejection path, or a straggling refusal would
        // cancel an order that has since been reserved and paid for
        Order order = orderAwaiting(OrderStatus.CONFIRMED, "a-newer-step");

        // when
        service(1).onStockRejected(ORDER_ID, STEP_ID, "INSUFFICIENT_STOCK");

        // then
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(outboxEventFactory, never()).orderCancelled(any(Order.class), any());
    }

    @Test
    @DisplayName("Records no cancellation when the rejection's write loses a race")
    void rejectionLosingTheRace_recordsNoOutcome() {
        // given
        orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(0).onStockRejected(ORDER_ID, STEP_ID, "INSUFFICIENT_STOCK");

        // then
        assertNull(meterRegistry.find(MetricNames.ORDERS_CANCELLED).counter());
    }

    @Test
    @DisplayName("Discards a reply naming a step the order is no longer waiting for")
    void replyForAnOldStep_isDiscarded() {
        // given - the sweep already gave up on this step and cleared it
        Order order = orderAwaiting(OrderStatus.CONFIRMED, "a-newer-step");

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then - advancing here would leave the order RESERVED against released stock
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orders, never()).replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class));
    }

    @Test
    @DisplayName("Discards a reply for an order with no step outstanding at all")
    void replyWhenNoStepOutstanding_isDiscarded() {
        // given - a redelivery of a reply that was already applied
        Order order = orderAwaiting(OrderStatus.RESERVED, null);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        verify(orders, never()).replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class));
    }

    @Test
    @DisplayName("Discards a reply that would drive an illegal move")
    void replyImplyingAnIllegalTransition_isDiscarded() {
        // given - a cancelled order cannot become reserved, whatever the reply says
        Order order = orderAwaiting(OrderStatus.CANCELLED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orders, never()).replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class));
    }

    @Test
    @DisplayName("Discards a reply for an order that no longer exists")
    void replyForUnknownOrder_isDiscarded() {
        // given
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(null);

        // when / then - must not throw; a deleted order is not an error condition here
        service(1).onStockReserved(ORDER_ID, STEP_ID);
        verify(orders, never()).replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class));
    }

    @Test
    @DisplayName("Records nothing when the conditional write loses a race")
    void writeLosingTheRace_recordsNoOutcome() {
        // given - something else moved the order between the read and the write
        orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(0).onStockReserved(ORDER_ID, STEP_ID);

        // then - the winning write has already acted; counting here would double-count
        assertNull(meterRegistry.find(MetricNames.ORDERS_RESERVED).counter());
    }

    @Test
    @DisplayName("Bumps the version so a concurrent write cannot also win")
    void commit_bumpsTheVersion() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);
        order.setVersion(4L);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then
        assertEquals(5L, order.getVersion());
    }

    // -- helpers ------------------------------------------------------------

    private Order orderAwaiting(OrderStatus status, String stepId) {
        Order order = new Order();
        order.id = new ObjectId(ORDER_ID);
        order.setStatus(status);
        order.setUserID("buyer");
        order.setSagaStepId(stepId);
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);
        return order;
    }
}
