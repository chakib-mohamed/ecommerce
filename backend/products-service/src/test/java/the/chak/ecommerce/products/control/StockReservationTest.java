package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.ProductRepository;

/**
 * Stock is held against an order, not decremented and forgotten. Today nothing reads {@code stock}
 * at all, so the catalog will sell the same unit to everyone who asks.
 *
 * <p>Two properties carry the weight. Reservation is keyed by order, because commands arrive
 * at-least-once and a redelivered reserve must not take the stock twice. And the decrement is
 * conditional on there being enough, because two orders reaching the last unit together would both
 * pass a read-then-write check.
 *
 * <p>Deliberately an integration test against a real database rather than a mocked
 * {@code *ServiceTest}: both properties are database behaviour - a conditional UPDATE and rows used
 * as idempotency records - and mocking them would assert the mock, not the guarantee.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@Tag("integration")
class StockReservationTest {

    @Inject
    StockReservationService reservationService;

    @Inject
    ProductRepository productRepository;

    @Test
    @DisplayName("Holds the requested quantity and takes it out of available stock")
    void reserve_sufficientStock_holdsAndDecrements() {
        // given
        UUID productId = productWithStock(10);
        String orderId = "order-" + UUID.randomUUID();

        // when
        boolean reserved = reservationService.reserve(orderId, List.of(line(productId, 3)));

        // then
        assertTrue(reserved, "a reservation within available stock should be accepted");
        assertEquals(7, stockOf(productId));
    }

    @Test
    @DisplayName("Refuses the reservation and takes nothing when stock is short")
    void reserve_insufficientStock_refusesAndLeavesStockAlone() {
        // given
        UUID productId = productWithStock(2);
        String orderId = "order-" + UUID.randomUUID();

        // when
        boolean reserved = reservationService.reserve(orderId, List.of(line(productId, 5)));

        // then
        assertFalse(reserved, "a reservation beyond available stock must be refused");
        assertEquals(2, stockOf(productId), "a refused reservation must not touch stock");
    }

    @Test
    @DisplayName("Takes the stock once when the same reservation is delivered twice")
    void reserve_redelivered_isIdempotent() {
        // given - commands arrive at-least-once, so this is the normal case, not an edge one
        UUID productId = productWithStock(10);
        String orderId = "order-" + UUID.randomUUID();

        // when
        boolean first = reservationService.reserve(orderId, List.of(line(productId, 3)));
        boolean second = reservationService.reserve(orderId, List.of(line(productId, 3)));

        // then
        assertTrue(first);
        assertTrue(second, "a redelivered reserve must report the original outcome");
        assertEquals(7, stockOf(productId), "stock must be taken once, not twice");
    }

    @Test
    @DisplayName("Reports the original refusal when a rejected reservation is delivered twice")
    void reserve_redeliveredAfterRejection_staysRejected() {
        // given
        UUID productId = productWithStock(1);
        String orderId = "order-" + UUID.randomUUID();

        // when
        boolean first = reservationService.reserve(orderId, List.of(line(productId, 5)));
        boolean second = reservationService.reserve(orderId, List.of(line(productId, 5)));

        // then
        assertFalse(first);
        assertFalse(second);
        assertEquals(1, stockOf(productId));
    }

    @Test
    @DisplayName("Refuses the whole reservation when any one line is short")
    void reserve_oneLineShort_refusesEveryLine() {
        // given - a partly filled order is not a thing the lifecycle has a state for
        UUID plenty = productWithStock(10);
        UUID scarce = productWithStock(1);
        String orderId = "order-" + UUID.randomUUID();

        // when
        boolean reserved = reservationService.reserve(orderId,
                List.of(line(plenty, 2), line(scarce, 5)));

        // then
        assertFalse(reserved);
        assertEquals(10, stockOf(plenty), "the satisfiable line must be rolled back too");
        assertEquals(1, stockOf(scarce));
    }

    @Test
    @DisplayName("Returns held stock to the catalog when the reservation is released")
    void release_heldReservation_returnsTheStock() {
        // given
        UUID productId = productWithStock(10);
        String orderId = "order-" + UUID.randomUUID();
        reservationService.reserve(orderId, List.of(line(productId, 4)));

        // when
        reservationService.release(orderId);

        // then
        assertEquals(10, stockOf(productId));
    }

    @Test
    @DisplayName("Returns the stock once when a release is delivered twice")
    void release_redelivered_isIdempotent() {
        // given - a compensation is a command like any other and arrives at-least-once
        UUID productId = productWithStock(10);
        String orderId = "order-" + UUID.randomUUID();
        reservationService.reserve(orderId, List.of(line(productId, 4)));

        // when
        reservationService.release(orderId);
        reservationService.release(orderId);

        // then
        assertEquals(10, stockOf(productId), "a second release must not inflate stock");
    }

    @Test
    @DisplayName("Ignores a release for an order that never reserved anything")
    void release_unknownOrder_isANoOp() {
        // given - releasing after a rejected reservation is a legitimate saga path
        UUID productId = productWithStock(10);

        // when / then - must not throw; there is simply nothing to give back
        reservationService.release("order-" + UUID.randomUUID());
        assertEquals(10, stockOf(productId));
    }

    // -- helpers ------------------------------------------------------------

    private static StockLine line(UUID productId, int quantity) {
        return new StockLine(productId.toString(), quantity);
    }

    private UUID productWithStock(int stock) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Product product = new Product();
            product.setUuid(UUID.randomUUID());
            product.setTitle("Stock test product");
            product.setStock(stock);
            productRepository.persist(product);
            return product.getUuid();
        });
    }

    private int stockOf(UUID productId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> productRepository.findByUuid(productId).getStock());
    }
}
