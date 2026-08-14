package the.chak.ecommerce.products.entity;

/** What became of a reservation. */
public enum ReservationStatus {

    /** Stock is out of the catalog and held for the order. */
    HELD,

    /** The hold was given back; the stock is on sale again. */
    RELEASED,

    /** There was not enough stock. Recorded so a redelivered command repeats the refusal. */
    REJECTED
}
