package the.chak.ecommerce.outbox;

import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Retention half of the transactional outbox, shared by every service. A scheduled tick periodically
 * deletes records that the relay already published longer ago than the configured retention window,
 * keeping the outbox small while preserving a short-term audit trail. Unpublished records are never
 * touched: only the relay clears a record, by stamping it published.
 *
 * <p>Concrete subclasses are the CDI beans: they must declare the {@code @Scheduled} trigger calling
 * {@link #purgeOldPublished()} and supply the storage-specific {@link #deletePublishedOlderThan} and
 * {@link #retention()} seams.
 */
public abstract class AbstractOutboxPurgeJob {

    private static final Logger LOG = Logger.getLogger(AbstractOutboxPurgeJob.class);

    /**
     * Deletes published records older than the retention window. Returns the number of records
     * removed so callers (and tests) can observe the outcome.
     */
    public long purgeOldPublished() {
        Instant cutoff = Instant.now().minus(retention());
        long deleted = deletePublishedOlderThan(cutoff);
        if (deleted > 0) {
            LOG.infof("Purged %d published outbox records older than %s", deleted, retention());
        }
        return deleted;
    }

    /**
     * Deletes published records whose published timestamp predates {@code cutoff}. Records not yet
     * relayed must never be matched, so they are retained regardless of age.
     *
     * @return the number of records deleted
     */
    protected abstract long deletePublishedOlderThan(Instant cutoff);

    /** How long a published record is kept before it is eligible for purge. */
    protected abstract Duration retention();
}
