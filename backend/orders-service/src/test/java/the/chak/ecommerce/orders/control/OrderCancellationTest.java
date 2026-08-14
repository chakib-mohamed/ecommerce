package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.orders.control.exceptions.IllegalOrderTransitionException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * A buyer cancelling their own order.
 *
 * <p>This path used to write the status and nothing else: no compensation, no event, and an
 * unconditional write that ignored the version every saga write depends on. Each of those is a
 * separate way for a cancellation to be wrong rather than merely incomplete, so each is asserted
 * here on its own.
 *
 * <p>The endpoint is published and authenticated but not wired into the UI, which is the only
 * reason none of this has been seen in practice.
 */
@ExtendWith(MockitoExtension.class)
class OrderCancellationTest {

    @InjectMocks
    OrderService orderService;

    @Mock
    OrderRepository orderRepository;

    @Mock
    SagaService sagaService;

    @Mock
    OutboxEventFactory outboxEventFactory;

    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    OrderStateMachine stateMachine = new OrderStateMachine();

    private Order stored(OrderStatus status, String stepId) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setUserID("buyer@ecommerce.test");
        order.setStatus(status);
        order.setPrice(BigDecimal.valueOf(100.0));
        order.setProducts(new ArrayList<>());
        order.setCreationDate(LocalDateTime.now().minusHours(1));
        order.setSagaStepId(stepId);
        if (stepId != null) {
            order.setStepDeadline(Instant.now().plusSeconds(300));
            order.setPaymentMethodRef("pm_card_visa");
        }
        when(orderRepository.findById(order.id)).thenReturn(order);
        return order;
    }

    private void writeSucceeds() {
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class))).thenReturn(true);
    }

    private OutboxEntry[] written() {
        ArgumentCaptor<OutboxEntry[]> entries = ArgumentCaptor.forClass(OutboxEntry[].class);
        verify(sagaService).commitOrder(any(Order.class), entries.capture());
        return entries.getValue();
    }

    @Test
    @DisplayName("Gives back the stock a confirmed order may already be holding")
    void cancel_confirmedOrder_releasesStock() {
        // given - the reserve command is out and may already have been acted on. Products-service
        // releases by order id and gives back only what is still held, so a release for a
        // reservation that never happened costs nothing; not sending one when it did leaks stock
        // that nothing else will ever free.
        Order order = stored(OrderStatus.CONFIRMED, "step-1");
        when(outboxEventFactory.releaseStock(any(), any())).thenReturn(new OutboxEntry());
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        // when
        orderService.cancelOrder(order.id.toString());

        // then
        verify(outboxEventFactory).releaseStock(any(), any());
        assertEquals(2, written().length, "the release and the cancellation, in one write");
    }

    @Test
    @DisplayName("Releases nothing for an order that never opened a saga step")
    void cancel_initiatedOrder_releasesNothing() {
        // given - INITIATED holds no stock, exactly as the expiry sweep assumes
        Order order = stored(OrderStatus.INITIATED, null);
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        verify(outboxEventFactory, never()).releaseStock(any(), any());
        assertEquals(1, written().length, "only the cancellation");
    }

    @Test
    @DisplayName("Announces the cancellation, so the stream carries the buyer's as well as the system's")
    void cancel_announcesTheCancellation() {
        // given - every other path that reaches CANCELLED emits this. Only the buyer's own
        // cancellation was silent, which made the cancellation stream quietly incomplete.
        Order order = stored(OrderStatus.INITIATED, null);
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        verify(outboxEventFactory).orderCancelled(any(Order.class), any());
    }

    @Test
    @DisplayName("Clears the outstanding step, so the deadline sweep stops seeing the order")
    void cancel_clearsTheSagaStep() {
        // given - left set, the sweep finds this order, discovers it cannot be cancelled again,
        // and clears the deadline while releasing nothing. That branch was written for shipped
        // orders and reads as routine, which is what made the leak invisible.
        Order order = stored(OrderStatus.CONFIRMED, "step-1");
        when(outboxEventFactory.releaseStock(any(), any())).thenReturn(new OutboxEntry());
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        assertNull(order.getSagaStepId(), "no step is outstanding on a cancelled order");
        assertNull(order.getStepDeadline(), "and nothing should sweep it");
    }

    @Test
    @DisplayName("Drops the payment token, which has no reason to outlive the order")
    void cancel_clearsThePaymentToken() {
        // given - payment.md section 4: the token is held only for the span between confirm and
        // capture, and cleared the moment that resolves. A cancellation resolves it.
        Order order = stored(OrderStatus.CONFIRMED, "step-1");
        when(outboxEventFactory.releaseStock(any(), any())).thenReturn(new OutboxEntry());
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        assertNull(order.getPaymentMethodRef());
    }

    @Test
    @DisplayName("Writes conditionally, so a cancellation cannot be erased by a saga reply")
    void cancel_usesTheVersionGuardedWrite() {
        // given / when
        Order order = stored(OrderStatus.INITIATED, null);
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        // then - persistOrUpdate replaces the document and leaves the version untouched, so a saga
        // write that read the same version still matches and overwrites the cancellation. The
        // buyer would be charged for an order they had cancelled.
        verify(orderRepository, never()).persistOrUpdate(any(Order.class));
        verify(sagaService).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    @Test
    @DisplayName("Refuses the cancellation that lost the write rather than reporting one that did not happen")
    void cancel_lostWrite_isRejected() {
        Order order = stored(OrderStatus.INITIATED, null);
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class))).thenReturn(false);

        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.cancelOrder(order.id.toString()));
        assertEquals(0.0, meterRegistry.counter(MetricNames.ORDERS_CANCELLED).count(), 0.001);
    }

    @Test
    @DisplayName("Refuses to cancel while the charge is already being taken")
    void cancel_reservedOrder_isRefused() {
        // given - RESERVED is not a resting place: the capture was commanded the moment stock came
        // back, so a cancellation here always races a charge in flight.
        Order order = stored(OrderStatus.RESERVED, "payment-step");

        // when / then - releasing the stock and cancelling would leave the capture to land against
        // an order that no longer exists to pay for, and there is no refund path in the platform to
        // undo it. Refusing is the only answer that cannot take a buyer's money for nothing. The
        // transition stays legal for the saga itself, which cancels here on a declined charge -
        // that one knows the money was *not* taken.
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.cancelOrder(order.id.toString()));
        verify(sagaService, never()).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    @Test
    @DisplayName("Counts the cancellation once it has actually committed")
    void cancel_countsOnlyOnSuccess() {
        Order order = stored(OrderStatus.INITIATED, null);
        when(outboxEventFactory.orderCancelled(any(Order.class), any())).thenReturn(new OutboxEntry());
        writeSucceeds();

        orderService.cancelOrder(order.id.toString());

        assertEquals(1.0, meterRegistry.counter(MetricNames.ORDERS_CANCELLED).count(), 0.001);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(OrderService.REASON_BUYER_CANCELLED, order.getStatusReason(),
                "a buyer changing their mind reads differently from a saga giving up");
    }
}
