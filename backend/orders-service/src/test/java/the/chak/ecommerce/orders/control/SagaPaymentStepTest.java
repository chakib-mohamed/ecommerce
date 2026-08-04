package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
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
import org.mockito.ArgumentCaptor;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * The saga's payment step, seen from the orchestrator.
 *
 * <p>Until now RESERVED was the end of the road: the stock reply cleared the step and left nothing
 * outstanding. Payment makes RESERVED a waypoint, and that changes what the stock reply has to do -
 * it must open the next step rather than close the saga, or an order would sit reserved forever with
 * stock held and no deadline to free it.
 *
 * <p>The reply guards are the same ones the stock step uses and are covered by {@link SagaServiceTest};
 * what is tested here is that they are actually applied to the payment replies too, because a stale
 * payment reply is the one that takes money against an order that no longer expects it.
 */
class SagaPaymentStepTest {

    private static final String ORDER_ID = new ObjectId().toString();
    private static final String STEP_ID = "step-1";
    private static final String PROVIDER_REF = "pi_123";
    private static final String PAYMENT_METHOD = "pm_card_visa";

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
        when(outboxEventFactory.releaseStock(any(), any())).thenReturn(new OutboxEntry());
        when(outboxEventFactory.orderPaid(any(Order.class))).thenReturn(new OutboxEntry());

        SagaService saga = new SagaService();
        saga.orderRepository = orderRepository;
        saga.outboxRepository = outboxRepository;
        saga.outboxEventFactory = outboxEventFactory;
        saga.outboxRelay = outboxRelay;
        saga.mongoClient = mongoClient;
        saga.stateMachine = new OrderStateMachine();
        saga.meterRegistry = meterRegistry;
        saga.stepTimeout = Duration.ofMinutes(5);
        return saga;
    }

    // -- reserving now opens the payment step -------------------------------

    @Test
    @DisplayName("Asks for payment once the stock is held")
    void stockReserved_commandsTheCapture() {
        // given
        orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then
        verify(outboxEventFactory).capturePayment(any(Order.class), any(), eq(PAYMENT_METHOD));
    }

    @Test
    @DisplayName("Keeps a step outstanding while the payment is in flight")
    void stockReserved_leavesTheNextStepOutstanding() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then - cleared instead, the order would hold stock forever: the sweep only ever looks at
        // orders with a deadline, so nothing would free it
        assertNotNull(order.getSagaStepId(), "payment is outstanding, so a step must be too");
        assertNotNull(order.getStepDeadline(), "an outstanding step the sweep cannot see is a leak");
    }

    @Test
    @DisplayName("Opens the payment step under a new id, not the one stock answered")
    void stockReserved_startsAFreshStep() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then - reusing the id would make a redelivered stock reply look current again
        assertNotNull(order.getSagaStepId(), "a step must be open for the payment reply to name");
        assertNotEquals(STEP_ID, order.getSagaStepId());
    }

    @Test
    @DisplayName("Commands the capture under the step the payment reply will name")
    void stockReserved_commandsTheCaptureUnderTheNewStep() {
        // given
        Order order = orderAwaiting(OrderStatus.CONFIRMED, STEP_ID);

        // when
        service(1).onStockReserved(ORDER_ID, STEP_ID);

        // then - a command carrying any other id would be answered by a reply the guard discards,
        // and the order would stall to its deadline having already been charged
        ArgumentCaptor<String> commandedStep = ArgumentCaptor.forClass(String.class);
        verify(outboxEventFactory).capturePayment(any(Order.class), commandedStep.capture(), any());
        assertEquals(order.getSagaStepId(), commandedStep.getValue());
    }

    // -- the payment succeeded ----------------------------------------------

    @Test
    @DisplayName("Advances a reserved order to paid when the money is taken")
    void paymentCaptured_advancesTheOrder() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertNull(order.getSagaStepId(), "the answered step is no longer outstanding");
        assertNull(order.getStepDeadline());
    }

    @Test
    @DisplayName("Forgets the payment reference once the charge has resolved")
    void paymentCaptured_clearsTheStoredToken() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then - the reference is single-use and has done its job; keeping it past the charge is
        // payment data held for no reason
        assertNull(order.getPaymentMethodRef());
    }

    @Test
    @DisplayName("Counts each paid order so revenue is observable")
    void paymentCaptured_isCounted() {
        // given - analytics counts confirmation as revenue today, which is orders placed, not money
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        assertEquals(1.0, meterRegistry.get(MetricNames.ORDERS_PAID).counter().count(), 0.001);
    }

    @Test
    @DisplayName("Announces the payment so revenue can be counted")
    void paymentCaptured_announcesTheSale() {
        // given - analytics counts money taken, and this is the only event that says it was
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        verify(outboxEventFactory).orderPaid(any(Order.class));
    }

    @Test
    @DisplayName("Writes the sale in the same transaction as the status change")
    void paymentCaptured_announcementCommitsWithTheStatus() {
        // given - written separately, a crash between them would either report revenue for an order
        // that never reached PAID, or take money and never report it
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        verify(outbox).insertOne(any(ClientSession.class), any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Announces nothing when the capture's write loses a race")
    void captureLosingTheRace_announcesNothing() {
        // given - the winning write has already announced this sale; a second would be counted as
        // a rewrite of the same rows at best, and double revenue at worst
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(0).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        verify(outbox, never()).insertOne(any(ClientSession.class), any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Counts nothing when the capture's write loses a race")
    void captureLosingTheRace_countsNothing() {
        // given - something else moved the order between the read and the write
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(0).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then - the winning write has already counted this order; counting again would overstate
        // revenue, which is the one number that must not drift
        assertNull(meterRegistry.find(MetricNames.ORDERS_PAID).counter());
    }

    @Test
    @DisplayName("Discards a capture naming a step the order is no longer waiting for")
    void captureForAnOldStep_isDiscarded() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, "a-newer-step");

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        verify(orders, never()).replaceOne(any(ClientSession.class), any(Bson.class), any(Order.class));
    }

    @Test
    @DisplayName("Discards a capture for an order the sweep has already cancelled")
    void captureForACancelledOrder_isDiscarded() {
        // given - the money was taken after the saga gave up; the order cannot un-cancel itself,
        // and reconciliation against the provider reference is what resolves this
        Order order = orderAwaiting(OrderStatus.CANCELLED, STEP_ID);

        // when
        service(1).onPaymentCaptured(ORDER_ID, STEP_ID, PROVIDER_REF);

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // -- the payment failed --------------------------------------------------

    @Test
    @DisplayName("Cancels the order when the charge is declined")
    void paymentFailed_cancelsTheOrder() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Gives back the stock the declined order was holding")
    void paymentFailed_releasesTheStock() {
        // given - unlike a stock refusal, this failure comes after something was taken
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then - without this the stock stays out of sale until nothing ever frees it: the step is
        // answered, so the deadline sweep will never look at this order again
        verify(outboxEventFactory).releaseStock(eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("Records why the charge failed where the buyer can see it")
    void paymentFailed_recordsTheReason() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then - "cancelled" with no reason is indistinguishable from a stock failure or the
        // buyer's own cancellation
        assertEquals("card_declined", order.getStatusReason());
    }

    @Test
    @DisplayName("Falls back to a stated reason when the failure gives none")
    void paymentFailed_withoutAReason_stillRecordsOne() {
        // given
        Order order = orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, null);

        // then
        assertEquals(SagaService.REASON_PAYMENT_FAILED, order.getStatusReason());
    }

    @Test
    @DisplayName("Frees the stock and announces the cancellation in one write")
    void paymentFailed_compensationAndNoticeCommitTogether() {
        // given - written separately, a crash between them frees stock without cancelling the
        // order, or cancels one whose stock is never returned
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then
        verify(outbox, org.mockito.Mockito.times(2))
                .insertOne(any(ClientSession.class), any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Discards a failure naming a step the order is no longer waiting for")
    void failureForAnOldStep_isDiscarded() {
        // given - a straggling decline must not cancel an order that has since been paid
        Order order = orderAwaiting(OrderStatus.RESERVED, "a-newer-step");

        // when
        service(1).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        verify(outboxEventFactory, never()).releaseStock(any(), any());
    }

    @Test
    @DisplayName("Counts nothing when the failure's write loses a race")
    void failureLosingTheRace_countsNothing() {
        // given
        orderAwaiting(OrderStatus.RESERVED, STEP_ID);

        // when
        service(0).onPaymentFailed(ORDER_ID, STEP_ID, "card_declined");

        // then
        assertNull(meterRegistry.find(MetricNames.ORDERS_CANCELLED).counter());
    }

    // -- helpers ------------------------------------------------------------

    private Order orderAwaiting(OrderStatus status, String stepId) {
        Order order = new Order();
        order.id = new ObjectId(ORDER_ID);
        order.setStatus(status);
        order.setUserID("buyer");
        order.setSagaStepId(stepId);
        order.setStepDeadline(Instant.now().plusSeconds(300));
        order.setPaymentMethodRef(PAYMENT_METHOD);
        when(orderRepository.findById(any(ObjectId.class))).thenReturn(order);
        return order;
    }
}
