package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * The sweep is the only thing that ever frees stock held by a stalled saga - reservations have no
 * expiry of their own, and the outbox relay abandons a poison record with nothing but a log line.
 *
 * <p>So the cases that matter are: it does compensate, it compensates even when nobody knows whether
 * the work happened, and it does not keep re-processing orders it cannot cancel.
 */
class SagaDeadlineSweepTest {

    private static final String STEP_ID = "step-1";

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final SagaService sagaService = mock(SagaService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private SagaDeadlineSweep sweep(boolean writeWins) {
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class)))
                .thenReturn(writeWins);
        when(outboxEventFactory.releaseStock(any(), any())).thenReturn(new OutboxEntry());
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());

        SagaDeadlineSweep job = new SagaDeadlineSweep();
        job.orderRepository = orderRepository;
        job.outboxEventFactory = outboxEventFactory;
        job.sagaService = sagaService;
        job.stateMachine = new OrderStateMachine();
        job.meterRegistry = meterRegistry;
        return job;
    }

    @Test
    @DisplayName("Cancels an order whose step ran past its deadline")
    void expiredStep_cancelsTheOrder() {
        // given
        Order order = expired(OrderStatus.CONFIRMED);

        // when
        sweep(true).sweep();

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Releases the stock the abandoned step may have been holding")
    void expiredStep_releasesTheStock() {
        // given - the step may or may not have taken stock; a timeout is exactly the case where
        // nobody knows, and release is idempotent so compensating regardless is safe
        expired(OrderStatus.CONFIRMED);

        // when
        sweep(true).sweep();

        // then
        verify(outboxEventFactory).releaseStock(any(), eq(STEP_ID));
    }

    @Test
    @DisplayName("Writes the release and the cancellation in one go")
    void expiredStep_compensationAndNoticeCommitTogether() {
        // given - written separately, a crash between them could free the stock without ever
        // announcing the cancellation, or announce one that never freed anything
        expired(OrderStatus.CONFIRMED);

        // when
        sweep(true).sweep();

        // then
        verify(sagaService).commitOrder(any(Order.class), any(OutboxEntry.class), any(OutboxEntry.class));
    }

    @Test
    @DisplayName("Clears the step so the order stops being swept")
    void expiredStep_clearsTheDeadline() {
        // given
        Order order = expired(OrderStatus.CONFIRMED);

        // when
        sweep(true).sweep();

        // then - left set, every sweep would pick the same order up again forever
        assertNull(order.getStepDeadline());
        assertNull(order.getSagaStepId());
    }

    @Test
    @DisplayName("Counts each abandoned saga so the stalls are visible")
    void expiredStep_isCounted() {
        // given - each one of these held stock; a log line alone is not observable
        expired(OrderStatus.CONFIRMED);

        // when
        sweep(true).sweep();

        // then
        assertEquals(1.0, meterRegistry.get(MetricNames.SAGAS_TIMED_OUT).counter().count(), 0.001);
    }

    @Test
    @DisplayName("Only clears the deadline when the order can no longer be cancelled")
    void expiredStepOnAnUncancellableOrder_clearsTheDeadlineOnly() {
        // given - shipped, say: cancelling is not a legal move from here
        Order order = expired(OrderStatus.SHIPPED);

        // when
        sweep(true).sweep();

        // then - the order keeps its status, but stops being swept forever
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNull(order.getStepDeadline());
        verify(outboxEventFactory, never()).releaseStock(any(), any());
    }

    @Test
    @DisplayName("Counts nothing when the conditional write loses a race")
    void writeLosingTheRace_countsNothing() {
        // given
        expired(OrderStatus.CONFIRMED);

        // when
        sweep(false).sweep();

        // then
        assertNull(meterRegistry.find(MetricNames.SAGAS_TIMED_OUT).counter());
    }

    @Test
    @DisplayName("Does nothing when no step has expired")
    void nothingExpired_doesNothing() {
        // given
        when(orderRepository.findExpiredSteps(any(Instant.class), anyInt())).thenReturn(List.of());

        // when
        sweep(true).sweep();

        // then
        verify(sagaService, never()).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    // -- helpers ------------------------------------------------------------

    private Order expired(OrderStatus status) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setStatus(status);
        order.setUserID("buyer");
        order.setSagaStepId(STEP_ID);
        order.setStepDeadline(Instant.now().minusSeconds(600));
        when(orderRepository.findExpiredSteps(any(Instant.class), anyInt()))
                .thenReturn(List.of(order));
        return order;
    }
}
