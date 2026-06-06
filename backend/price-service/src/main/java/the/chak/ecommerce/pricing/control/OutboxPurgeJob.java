package the.chak.ecommerce.pricing.control;

import java.time.Duration;
import java.time.Instant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.repository.OutboxRepository;

/**
 * Retention half of the transactional outbox. A scheduled tick periodically deletes entries that the
 * {@link OutboxRelay} already published longer ago than the configured retention window, keeping the
 * {@code outbox} collection small while preserving a short-term audit trail. Unpublished entries are
 * never touched: only the relay clears an entry, by stamping {@code publishedAt}. The delete is a
 * single-collection operation, so - unlike the JPA reference - no transaction is needed.
 */
@ApplicationScoped
public class OutboxPurgeJob {

    private static final Logger LOG = Logger.getLogger(OutboxPurgeJob.class);

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "pricing.outbox.retention", defaultValue = "P7D")
    Duration retention;

    @Scheduled(every = "{pricing.outbox.purge-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeOldPublished();
    }

    /**
     * Deletes published entries older than the retention window. Returns the number of entries
     * removed so callers (and tests) can observe the outcome.
     */
    public long purgeOldPublished() {
        Instant cutoff = Instant.now().minus(retention);
        long deleted = outboxRepository.deletePublishedOlderThan(cutoff);
        if (deleted > 0) {
            LOG.infof("Purged %d published outbox entries older than %s", deleted, retention);
        }
        return deleted;
    }
}
