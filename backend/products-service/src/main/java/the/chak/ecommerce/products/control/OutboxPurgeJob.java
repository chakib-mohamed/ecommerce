package the.chak.ecommerce.products.control;

import java.time.Duration;
import java.time.Instant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.outbox.AbstractOutboxPurgeJob;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Product-specific outbox purge. The retention logic lives in {@link AbstractOutboxPurgeJob}; this
 * subclass supplies the scheduled trigger, the retention window, and the transactional delete that
 * keeps the {@code outbox} table and its partial index small.
 */
@ApplicationScoped
public class OutboxPurgeJob extends AbstractOutboxPurgeJob {

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "products.outbox.retention", defaultValue = "P7D")
    Duration retention;

    @Scheduled(every = "{products.outbox.purge-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeOldPublished();
    }

    @Override
    @Transactional
    protected long deletePublishedOlderThan(Instant cutoff) {
        return outboxRepository.deletePublishedOlderThan(cutoff);
    }

    @Override
    protected Duration retention() {
        return retention;
    }
}
