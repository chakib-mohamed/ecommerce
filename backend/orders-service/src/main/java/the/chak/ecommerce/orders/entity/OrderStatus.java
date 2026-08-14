package the.chak.ecommerce.orders.entity;

/**
 * Where an order sits in its lifecycle. The legal moves between these are defined by
 * {@link the.chak.ecommerce.orders.control.OrderStateMachine}; see
 * {@code docs/specs/order-lifecycle.md} for the full table.
 *
 * <p>{@link #INITIATED} is the only state in which an order may be changed or removed.
 * {@link #DELIVERED}, {@link #CANCELLED} and {@link #REFUNDED} are terminal.
 */
public enum OrderStatus {
    INITIATED,
    CONFIRMED,
    RESERVED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;
}
