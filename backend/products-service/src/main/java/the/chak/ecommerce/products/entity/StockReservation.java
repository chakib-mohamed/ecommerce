package the.chak.ecommerce.products.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * Stock held against one order line.
 *
 * <p>The row is the idempotency record as much as the accounting one: reserve and release both
 * arrive at-least-once, and the presence of a row for an order is what tells a redelivered command
 * that its work is already done.
 *
 * <p>A rejected reservation is recorded too, with {@link ReservationStatus#REJECTED}, so that
 * redelivering it reports the original refusal instead of re-testing stock that has since moved.
 */
@Getter
@Setter
@Entity
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The order the stock is held for. Reservation is keyed by this. */
    private String orderId;

    private String productId;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime createdAt;
}
