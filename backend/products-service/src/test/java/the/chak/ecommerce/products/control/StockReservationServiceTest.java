package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.entity.ReservationStatus;
import the.chak.ecommerce.products.entity.StockReservation;
import the.chak.ecommerce.products.repository.ProductRepository;
import the.chak.ecommerce.products.repository.StockReservationRepository;

/**
 * The decision-making in {@link StockReservationService}, with the database mocked out: which
 * lines get taken, what happens to the ones already taken when a later line is short, and how a
 * redelivered command is answered.
 *
 * <p>{@code StockReservationTest} covers the same service against a real database, where the
 * conditional decrement and the idempotency rows actually live. Both are needed - a
 * {@code @QuarkusTest} contributes no JaCoCo coverage, because Quarkus loads transformed copies of
 * the classes the agent instrumented.
 */
class StockReservationServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String PRODUCT_A = "11111111-1111-1111-1111-111111111111";
    private static final String PRODUCT_B = "22222222-2222-2222-2222-222222222222";

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final StockReservationRepository reservationRepository =
            mock(StockReservationRepository.class);

    private StockReservationService service() {
        StockReservationService service = new StockReservationService();
        service.productRepository = productRepository;
        service.reservationRepository = reservationRepository;
        return service;
    }

    @Test
    @DisplayName("Takes every line when the catalog can meet all of them")
    void reserve_allLinesAvailable_isHeld() {
        // given
        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(productRepository.decrementStockIfAvailable(any(UUID.class), anyInt())).thenReturn(true);

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 2), line(PRODUCT_B, 1)));

        // then
        assertTrue(held);
        verify(productRepository, never()).incrementStock(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Gives back the lines already taken when a later line is short")
    void reserve_laterLineShort_restoresTheEarlierOnes() {
        // given - the first line is met, the second is not
        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(productRepository.decrementStockIfAvailable(eq(UUID.fromString(PRODUCT_A)), eq(2)))
                .thenReturn(true);
        when(productRepository.decrementStockIfAvailable(eq(UUID.fromString(PRODUCT_B)), eq(5)))
                .thenReturn(false);

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 2), line(PRODUCT_B, 5)));

        // then - the order is all-or-nothing, so the met line cannot stay taken
        assertFalse(held);
        verify(productRepository).incrementStock(UUID.fromString(PRODUCT_A), 2);
        verify(productRepository, never()).incrementStock(UUID.fromString(PRODUCT_B), 5);
    }

    @Test
    @DisplayName("Restores nothing when the very first line is short")
    void reserve_firstLineShort_restoresNothing() {
        // given
        when(reservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(productRepository.decrementStockIfAvailable(any(UUID.class), anyInt())).thenReturn(false);

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 9)));

        // then
        assertFalse(held);
        verify(productRepository, never()).incrementStock(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Repeats the original hold without touching stock again")
    void reserve_redeliveredAfterHold_reportsHeldAndDecrementsNothing() {
        // given - at-least-once delivery makes this the normal case
        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(reservation(PRODUCT_A, 2, ReservationStatus.HELD)));

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 2)));

        // then
        assertTrue(held);
        verify(productRepository, never()).decrementStockIfAvailable(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Repeats the original refusal rather than re-testing stock that has since moved")
    void reserve_redeliveredAfterRejection_reportsRejected() {
        // given
        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(reservation(PRODUCT_A, 9, ReservationStatus.REJECTED)));

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 9)));

        // then - restocking since the refusal must not silently turn it into a hold
        assertFalse(held);
        verify(productRepository, never()).decrementStockIfAvailable(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Treats an already-released reservation as still decided")
    void reserve_redeliveredAfterRelease_doesNotTakeStockAgain() {
        // given - released is not rejected: the order did hold stock once
        when(reservationRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(reservation(PRODUCT_A, 2, ReservationStatus.RELEASED)));

        // when
        boolean held = service().reserve(ORDER_ID, List.of(line(PRODUCT_A, 2)));

        // then
        assertTrue(held);
        verify(productRepository, never()).decrementStockIfAvailable(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Puts each held line back on sale when released")
    void release_heldLines_areReturnedAndMarked() {
        // given
        StockReservation held = reservation(PRODUCT_A, 3, ReservationStatus.HELD);
        when(reservationRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.HELD))
                .thenReturn(List.of(held));

        // when
        service().release(ORDER_ID);

        // then
        verify(productRepository).incrementStock(UUID.fromString(PRODUCT_A), 3);
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, held.getStatus());
    }

    @Test
    @DisplayName("Does nothing when the order holds no stock")
    void release_nothingHeld_isANoOp() {
        // given - the path taken after a rejected reservation, and on a redelivered release
        when(reservationRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.HELD))
                .thenReturn(List.of());

        // when
        service().release(ORDER_ID);

        // then
        verify(productRepository, never()).incrementStock(any(UUID.class), anyInt());
    }

    // -- helpers ------------------------------------------------------------

    private static StockLine line(String productId, int quantity) {
        return new StockLine(productId, quantity);
    }

    private static StockReservation reservation(String productId, int quantity,
            ReservationStatus status) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(ORDER_ID);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        return reservation;
    }
}
