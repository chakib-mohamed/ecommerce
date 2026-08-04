package the.chak.ecommerce.payment.entity;

/** What became of a capture attempt. There is no in-flight state: see {@code Payment}. */
public enum PaymentStatus {
    CAPTURED,
    FAILED,
    REFUNDED
}
