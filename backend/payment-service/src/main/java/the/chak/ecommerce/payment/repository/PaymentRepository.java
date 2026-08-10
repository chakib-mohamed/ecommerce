package the.chak.ecommerce.payment.repository;

import java.util.Optional;
import java.util.UUID;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.payment.entity.Payment;

@ApplicationScoped
public class PaymentRepository implements PanacheRepositoryBase<Payment, UUID> {

    /**
     * The record of one saga attempt's charge, if it has already been made.
     *
     * <p>This lookup is what makes a redelivered command cheap: the recorded outcome is replayed
     * without going near the provider. It is an optimisation, not the safety guarantee - that is
     * the idempotency key, which still holds if this row is lost.
     */
    public Optional<Payment> findAttempt(String orderId, String stepId) {
        return find("orderId = ?1 and stepId = ?2", orderId, stepId).firstResultOptional();
    }
}
