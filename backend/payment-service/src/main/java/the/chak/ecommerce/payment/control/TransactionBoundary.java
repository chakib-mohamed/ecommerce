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
 * <p>Annotations were not an option regardless. {@code @Transactional} is applied by an
 * interceptor, and an interceptor only runs when the call arrives through the bean's proxy - a
 * method calling its own sibling with {@code this} silently gets no transaction at all. That is
 * exactly what happened here: {@code record} was annotated, called from {@code capture}, and never
 * ran in a transaction once in production.
 *
 * <p>Being an injected collaborator also keeps {@link PaymentService} testable without a container:
 * a test supplies a boundary that simply runs the work.
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
