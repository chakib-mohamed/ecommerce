package the.chak.ecommerce.products.control;

import java.time.Duration;
import java.time.Instant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Retention half of the transactional outbox. A scheduled tick periodically deletes rows that the
 * {@link OutboxRelay} already published longer ago than the configured retention window, keeping the
 * {@code outbox} table and its partial index small while preserving a short-term audit trail.
 * Unpublished rows are never touched: only the relay clears a row, by stamping {@code published_at}.
 */
@ApplicationScoped
public class OutboxPurgeJob {

    private static final Logger LOG = Logger.getLogger(OutboxPurgeJob.class);

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "products.outbox.retention", defaultValue = "P7D")
    Duration retention;

    @Scheduled(every = "{products.outbox.purge-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeOldPublished();
    }

    /**
     * Deletes published rows older than the retention window. Returns the number of rows removed so
     * callers (and tests) can observe the outcome.
     */
    @Transactional
    public long purgeOldPublished() {
        Instant cutoff = Instant.now().minus(retention);
        long deleted = outboxRepository.deletePublishedOlderThan(cutoff);
        if (deleted > 0) {
            LOG.infof("Purged %d published outbox rows older than %s", deleted, retention);
        }
        return deleted;
    }
}
