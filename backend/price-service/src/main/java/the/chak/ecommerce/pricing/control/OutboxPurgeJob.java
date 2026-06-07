package the.chak.ecommerce.pricing.control;

import java.time.Duration;
import java.time.Instant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.outbox.AbstractOutboxPurgeJob;
import the.chak.ecommerce.pricing.repository.OutboxRepository;

/**
 * Price-specific outbox purge. The retention logic lives in {@link AbstractOutboxPurgeJob}; this
 * subclass supplies the scheduled trigger, the retention window, and the single-collection delete
 * (no transaction needed for a single Mongo operation).
 */
@ApplicationScoped
public class OutboxPurgeJob extends AbstractOutboxPurgeJob {

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "pricing.outbox.retention", defaultValue = "P7D")
    Duration retention;

    @Scheduled(every = "{pricing.outbox.purge-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeOldPublished();
    }

    @Override
    protected long deletePublishedOlderThan(Instant cutoff) {
        return outboxRepository.deletePublishedOlderThan(cutoff);
    }

    @Override
    protected Duration retention() {
        return retention;
    }
}
