package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import the.chak.ecommerce.products.control.events.ReleaseStockCommand;
import the.chak.ecommerce.products.control.events.ReserveStockCommand;
import the.chak.ecommerce.products.control.events.ReserveStockLine;
import the.chak.ecommerce.products.control.events.StockRejectedEvent;
import the.chak.ecommerce.products.control.events.StockReservedEvent;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * The catalog's side of the saga's stock step: what it does, and what it says it did.
 *
 * <p>The reply matters as much as the work. If the stock committed but the reply did not, the order
 * would wait forever on an answer that had already happened - which is why the reply is an outbox
 * row written in the same transaction rather than a send to Kafka.
 */
class StockCommandHandlerTest {

    private static final String ORDER_ID = "6512c0ffee00000000000001";
    private static final String STEP_ID = "step-1";
    private static final String PRODUCT_ID = "11111111-1111-1111-1111-111111111111";

    private final StockReservationService reservationService = mock(StockReservationService.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);

    private StockCommandHandler handler() {
        StockCommandHandler handler = new StockCommandHandler();
        handler.reservationService = reservationService;
        handler.outboxRepository = outboxRepository;
        handler.outboxEventFactory = outboxEventFactory;
        handler.outboxRelay = outboxRelay;
        return handler;
    }

    @Test
    @DisplayName("Replies stock-reserved when the catalog can meet the order")
    void handleReserve_held_repliesReserved() {
        // given
        when(reservationService.reserve(eq(ORDER_ID), anyList())).thenReturn(true);
        when(outboxEventFactory.stockReserved(anyString(), any(StockReservedEvent.class)))
                .thenReturn(new OutboxEvent());

        // when
        handler().handleReserve(reserveCommand(2));

        // then
        ArgumentCaptor<StockReservedEvent> reply = ArgumentCaptor.forClass(StockReservedEvent.class);
        verify(outboxEventFactory).stockReserved(eq(ORDER_ID), reply.capture());
        verify(outboxRepository).persist(any(OutboxEvent.class));
        assertEquals(STEP_ID, reply.getValue().getStepId(),
                "the reply must carry back the step it answers");
    }

    @Test
    @DisplayName("Replies stock-rejected when the catalog cannot meet the order")
    void handleReserve_rejected_repliesRejected() {
        // given
        when(reservationService.reserve(eq(ORDER_ID), anyList())).thenReturn(false);
        when(outboxEventFactory.stockRejected(anyString(), any(StockRejectedEvent.class)))
                .thenReturn(new OutboxEvent());

        // when
        handler().handleReserve(reserveCommand(99));

        // then - a refusal is an answer, not silence; without it the order stalls to its deadline
        ArgumentCaptor<StockRejectedEvent> reply = ArgumentCaptor.forClass(StockRejectedEvent.class);
        verify(outboxEventFactory).stockRejected(eq(ORDER_ID), reply.capture());
        verify(outboxRepository).persist(any(OutboxEvent.class));
        assertEquals(STEP_ID, reply.getValue().getStepId());
        verify(outboxEventFactory, never()).stockReserved(anyString(), any());
    }

    @Test
    @DisplayName("Names why the reservation was refused")
    void handleReserve_rejected_carriesAReason() {
        // given
        when(reservationService.reserve(eq(ORDER_ID), anyList())).thenReturn(false);
        when(outboxEventFactory.stockRejected(anyString(), any(StockRejectedEvent.class)))
                .thenReturn(new OutboxEvent());

        // when
        handler().handleReserve(reserveCommand(99));

        // then
        ArgumentCaptor<StockRejectedEvent> reply = ArgumentCaptor.forClass(StockRejectedEvent.class);
        verify(outboxEventFactory).stockRejected(eq(ORDER_ID), reply.capture());
        assertEquals("INSUFFICIENT_STOCK", reply.getValue().getReason());
    }

    @Test
    @DisplayName("Passes every command line through to the reservation")
    void handleReserve_mapsEveryLine() {
        // given
        when(reservationService.reserve(eq(ORDER_ID), anyList())).thenReturn(true);
        when(outboxEventFactory.stockReserved(anyString(), any(StockReservedEvent.class)))
                .thenReturn(new OutboxEvent());
        ReserveStockCommand command = new ReserveStockCommand(ORDER_ID, STEP_ID,
                List.of(new ReserveStockLine(PRODUCT_ID, 2), new ReserveStockLine(PRODUCT_ID, 3)));

        // when
        handler().handleReserve(command);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(reservationService).reserve(eq(ORDER_ID), lines.capture());
        assertEquals(2, lines.getValue().size());
        assertEquals(3, lines.getValue().get(1).quantity());
    }

    @Test
    @DisplayName("Releases the order's stock and sends no reply")
    void handleRelease_releasesAndStaysSilent() {
        // when
        handler().handleRelease(new ReleaseStockCommand(ORDER_ID, STEP_ID));

        // then - a compensation has no outcome the orchestrator could act on; the order is already
        // on its way to CANCELLED and there is no state for "failed to release"
        verify(reservationService).release(ORDER_ID);
        verify(outboxRepository, never()).persist(any(OutboxEvent.class));
    }

    // -- helpers ------------------------------------------------------------

    private static ReserveStockCommand reserveCommand(int quantity) {
        return new ReserveStockCommand(ORDER_ID, STEP_ID,
                List.of(new ReserveStockLine(PRODUCT_ID, quantity)));
    }
}
