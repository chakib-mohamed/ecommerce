package the.chak.ecommerce.payment.control;

import java.util.function.Supplier;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runs a piece of work in its own transaction.
 *
 * <p>Exists because {@link PaymentService#capture} cannot be {@code @Transactional}: it calls the
 * payment provider over the network, and a transaction held open across that call would tie a
 * database connection to a remote timeout for no benefit - a rollback cannot un-charge a card.
 * The transaction has to wrap the database work on either side of that call and nothing else, so
 * the boundary is stated here rather than implied by an annotation.
 *
 * <p>Annotating the two halves would work too. ArC intercepts by subclassing, so - unlike Spring's
 * proxy-based AOP - a call to a non-private sibling through {@code this} <em>is</em> intercepted;
 * products-service's {@code deleteReview -> removeReview} has always relied on that. What an
 * annotation cannot express is the requirement pointing the other way: that the provider is called
 * with <em>no</em> transaction open. Nothing about the code says so, adding {@code @Transactional}
 * to {@code capture} would quietly undo it, and it is forbidden by convention (backend/CLAUDE.md:
 * no network I/O inside a transaction).
 *
 * <p>Being an injected collaborator is what lets {@link TransactionBoundaryTest} assert both
 * directions, and it keeps {@link PaymentService} testable without a container.
 */
@ApplicationScoped
public class TransactionBoundary {

    /** Runs {@code work} in a new transaction and returns its result. */
    public <T> T call(Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(work::get);
    }

    /** Runs {@code work} in a new transaction. */
    public void run(Runnable work) {
        QuarkusTransaction.requiringNew().run(work);
    }
}
