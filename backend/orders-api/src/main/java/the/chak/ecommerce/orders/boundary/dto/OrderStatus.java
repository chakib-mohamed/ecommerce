package the.chak.ecommerce.orders.boundary.dto;

/**
 * Where an order sits in its lifecycle, as reported over the API and on the
 * {@code order-initiated} event.
 *
 * <p>Kept in step with the orders-service entity enum of the same name: the outbox event factory
 * converts between the two by name, so a constant added here must be added there too.
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
