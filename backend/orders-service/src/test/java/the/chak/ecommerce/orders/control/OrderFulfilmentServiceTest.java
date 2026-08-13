package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * The two things the endpoint tests cannot reach: what gets counted, and what happens when two
 * callers move the same order at once.
 *
 * <p>Both are asserted here rather than over HTTP because a conditional write that loses a race is
 * not something a request can be made to lose on demand - the loss has to be arranged.
 */
@ExtendWith(MockitoExtension.class)
class OrderFulfilmentServiceTest {

    @InjectMocks
    OrderService orderService;

    @Mock
    OrderRepository orderRepository;

    @Mock
    SagaService sagaService;

    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    OrderStateMachine stateMachine = new OrderStateMachine();

    private Order stored(OrderStatus status) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setUserID("buyer@ecommerce.test");
        order.setStatus(status);
        order.setPrice(BigDecimal.valueOf(100.0));
        order.setProducts(new ArrayList<>());
        order.setCreationDate(LocalDateTime.now().minusDays(1));
        when(orderRepository.findById(order.id)).thenReturn(order);
        return order;
    }

    private void writeSucceeds() {
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class))).thenReturn(true);
    }

    @Test
    @DisplayName("Counts a dispatch, and counts it apart from a delivery")
    void shipOrder_countsShippedOnly() {
        // given
        Order order = stored(OrderStatus.PAID);
        writeSucceeds();

        // when
        orderService.shipOrder(order.id.toString());

        // then - the gap between paid and shipped is the fulfilment backlog, so folding these into
        // one "fulfilled" counter would hide the only number worth watching
        assertEquals(1.0, meterRegistry.counter(MetricNames.ORDERS_SHIPPED).count(), 0.001);
        assertEquals(0.0, meterRegistry.counter(MetricNames.ORDERS_DELIVERED).count(), 0.001);
    }

    @Test
    @DisplayName("Counts a delivery")
    void deliverOrder_countsDelivered() {
        Order order = stored(OrderStatus.SHIPPED);
        writeSucceeds();

        orderService.deliverOrder(order.id.toString());

        assertEquals(1.0, meterRegistry.counter(MetricNames.ORDERS_DELIVERED).count(), 0.001);
    }

    @Test
    @DisplayName("Publishes nothing, because no reader of an order-shipped event exists")
    void shipOrder_writesNoEvent() {
        // given
        Order order = stored(OrderStatus.PAID);
        writeSucceeds();

        // when
        orderService.shipOrder(order.id.toString());

        // then - an event nobody consumes costs the publish and looks like a working integration.
        // order-initiated was deleted from this service for exactly that; this asserts the second
        // one never arrives. See docs/specs/order-fulfilment.md section 6.
        var entries = org.mockito.ArgumentCaptor.forClass(OutboxEntry[].class);
        verify(sagaService).commitOrder(any(Order.class), entries.capture());
        assertEquals(0, entries.getValue().length, "fulfilment publishes no event");
    }

    @Test
    @DisplayName("Refuses the caller who loses the write, rather than reporting a dispatch it did not make")
    void shipOrder_lostWrite_isRejected() {
        // given - two operators at once, or one tool retrying: both read PAID, both pass the state
        // machine, and only one write can land
        Order order = stored(OrderStatus.PAID);
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class))).thenReturn(false);

        // when / then - 409, the same answer the loser would have got had it read a moment later
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.shipOrder(order.id.toString()));
    }

    @Test
    @DisplayName("Counts nothing when the write was lost")
    void shipOrder_lostWrite_countsNothing() {
        // given
        Order order = stored(OrderStatus.PAID);
        when(sagaService.commitOrder(any(Order.class), any(OutboxEntry[].class))).thenReturn(false);

        // when
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.shipOrder(order.id.toString()));

        // then - counting here would double-count the one dispatch that did happen
        assertEquals(0.0, meterRegistry.counter(MetricNames.ORDERS_SHIPPED).count(), 0.001);
    }

    @Test
    @DisplayName("Refuses an illegal move without attempting a write at all")
    void shipOrder_unpaidOrder_neverReachesTheDatabase() {
        // given
        Order order = stored(OrderStatus.RESERVED);

        // when / then
        assertThrows(IllegalOrderTransitionException.class,
                () -> orderService.shipOrder(order.id.toString()));
        verify(sagaService, never()).commitOrder(any(Order.class), any(OutboxEntry[].class));
    }

    @Test
    @DisplayName("Reports an order that does not exist as nothing, for the resource to turn into a 404")
    void shipOrder_unknownOrder_returnsNull() {
        // given
        ObjectId missing = new ObjectId();
        when(orderRepository.findById(missing)).thenReturn(null);

        // when / then - null rather than an exception: absence is the resource's answer to give
        org.junit.jupiter.api.Assertions
                .assertNull(orderService.shipOrder(missing.toString()));
    }
}
