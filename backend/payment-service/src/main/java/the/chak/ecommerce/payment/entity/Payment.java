package the.chak.ecommerce.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * The record of a charge: which order it was for, which saga attempt asked, and what the provider
 * said. This is the one thing the provider cannot tell us on its own.
 *
 * <p>Deliberately absent: the payment method reference. It is used to make the charge and then
 * forgotten - keeping it would be holding a payment credential for no reason once the provider's
 * own reference exists. Card number, expiry and CVV are never seen by this service at all.
 */
@Entity
@Getter
@Setter
@Table(name = "payment",
        uniqueConstraints = @UniqueConstraint(columnNames = { "order_id", "step_id" }))
public class Payment {

    @Id
    @Column(name = "id")
    private java.util.UUID id;

    /** The order the charge belongs to. */
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /**
     * The saga attempt that asked for the charge, and the idempotency key sent to the provider.
     * Unique with {@code orderId}: a redelivered command finds this row rather than charging again.
     */
    @Column(name = "step_id", nullable = false)
    private String stepId;

    /** The provider's identifier for the charge - what a refund and any reconciliation goes by. */
    @Column(name = "provider_ref")
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    /** The provider's reason for refusing, kept so the buyer can be told more than "declined". */
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
