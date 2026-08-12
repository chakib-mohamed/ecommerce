package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * Expiring a quote nobody committed.
 *
 * <p>The cases that matter are what separates this from the saga sweep: it must compensate nothing,
 * because an INITIATED order holds no stock and has taken no money; and it must lose to a buyer who
 * confirms in the window between the sweep reading the order and writing it back.
 */
class InitiatedOrderExpirySweepTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final SagaService sagaService = mock(SagaService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private InitiatedOrderExpirySweep sweep(boolean writeWins) {
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class)))
                .thenReturn(writeWins);
        when(outboxEventFactory.orderCancelled(any(Order.class), any()))
                .thenReturn(new OutboxEntry());

        InitiatedOrderExpirySweep job = new InitiatedOrderExpirySweep();
        job.orderRepository = orderRepository;
        job.outboxEventFactory = outboxEventFactory;
        job.sagaService = sagaService;
        job.meterRegistry = meterRegistry;
        job.ttl = Duration.ofHours(24);
        return job;
    }

    private Order initiated() {
        Order order = new Order();
        order.id = new ObjectId();
        order.setUserID("buyer@ecommerce.test");
        order.setStatus(OrderStatus.INITIATED);
        order.setCreationDate(LocalDateTime.now().minusDays(3));
        return order;
    }

    @Test
    @DisplayName("Cancels an order left uncommitted past its time to live")
    void staleInitiatedOrder_isCancelled() {
        // given
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        // when
        sweep(true).sweep();

        // then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Says why it was cancelled, so it reads differently from a buyer changing their mind")
    void expiredOrder_recordsWhy() {
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        sweep(true).sweep();

        assertEquals(InitiatedOrderExpirySweep.REASON_EXPIRED, order.getStatusReason());
    }

    @Test
    @DisplayName("Releases nothing, because an uncommitted order is holding nothing")
    void expiredOrder_issuesNoCompensation() {
        // given - the difference from the saga sweep, which must give back what its step took
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        // when
        sweep(true).sweep();

        // then - a release for stock that was never reserved would be a no-op at products-service,
        // but issuing one would still say this order had held something. It never did.
        verify(outboxEventFactory, never()).releaseStock(any(), any());
        ArgumentCaptor<OutboxEntry[]> written = ArgumentCaptor.forClass(OutboxEntry[].class);
        verify(sagaService).commitOrder(any(Order.class), written.capture());
        assertEquals(1, written.getValue().length,
                "exactly one entry - the cancellation, and nothing to undo");
    }

    @Test
    @DisplayName("Announces the cancellation so the stream carries every one of them")
    void expiredOrder_isAnnounced() {
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        sweep(true).sweep();

        verify(outboxEventFactory)
                .orderCancelled(any(Order.class), any());
    }

    @Test
    @DisplayName("Counts each expiry apart from a buyer's own cancellation")
    void expiredOrder_isCounted() {
        // given / when
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));
        sweep(true).sweep();

        // then - a rise here says something about checkout, not about demand, so it must not be
        // folded into the count of orders buyers chose to cancel
        assertEquals(1.0, meterRegistry.counter(MetricNames.ORDERS_EXPIRED).count(), 0.001);
        assertEquals(0.0, meterRegistry.counter(MetricNames.ORDERS_CANCELLED).count(), 0.001);
    }

    @Test
    @DisplayName("Leaves an order the buyer confirmed while the sweep was mid-flight")
    void orderConfirmedDuringTheSweep_isLeftAlone() {
        // given - the query selected it as INITIATED, then the buyer confirmed. The read and the
        // write are not atomic, so this window is real rather than theoretical.
        Order order = initiated();
        order.setStatus(OrderStatus.RESERVED);
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        // when
        sweep(true).sweep();

        // then - and note the guard cannot be "may it be cancelled": RESERVED -> CANCELLED is a
        // legal transition, so that question answers yes and this sweep would cancel an order
        // holding stock while releasing none of it. The question is whether it is still a quote.
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        verify(sagaService, never()).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    @Test
    @DisplayName("Counts nothing when the conditional write loses a race")
    void lostWrite_countsNothing() {
        Order order = initiated();
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of(order));

        sweep(false).sweep();

        assertEquals(0.0, meterRegistry.counter(MetricNames.ORDERS_EXPIRED).count(), 0.001);
    }

    @Test
    @DisplayName("Does nothing when no order has expired")
    void nothingExpired_writesNothing() {
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of());

        sweep(true).sweep();

        verify(sagaService, never()).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    @Test
    @DisplayName("Asks for orders older than the configured time to live")
    void sweep_usesTheConfiguredTtl() {
        // given
        when(orderRepository.findExpiredInitiated(any(), anyInt())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusHours(24);

        // when
        sweep(true).sweep();

        // then - the cutoff is now minus the TTL, not some other horizon. A sweep asking for the
        // wrong window either cancels live orders or never fires, and both look like nothing.
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findExpiredInitiated(cutoff.capture(), anyInt());
        LocalDateTime after = LocalDateTime.now().minusHours(24);
        assertTrue(!cutoff.getValue().isBefore(before) && !cutoff.getValue().isAfter(after),
                "cutoff " + cutoff.getValue() + " should sit within the sweep's own execution window");
    }
}
