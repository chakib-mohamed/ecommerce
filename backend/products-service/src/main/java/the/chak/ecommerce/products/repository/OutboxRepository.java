package the.chak.ecommerce.products.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.OutboxEvent;

@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    /**
     * Fetches a batch of not-yet-published, not-yet-failed rows for the relay, oldest first.
     * {@code FOR UPDATE SKIP LOCKED} keeps two concurrent fetches from returning the same rows,
     * so multiple instances can run the relay in parallel. The row locks are released when this
     * fetch transaction commits - before the Kafka send - so a row can still be published more
     * than once (two instances fetching either side of the commit, or a crash re-sending). That
     * is by design: delivery is at-least-once and idempotent consumers absorb the duplicates.
     */
    public List<OutboxEvent> findUnpublishedForUpdate(int batchSize) {
        return getEntityManager()
                .createNativeQuery(
                        "SELECT * FROM outbox WHERE published_at IS NULL AND failed_at IS NULL "
                                + "ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
                        OutboxEvent.class)
                .setParameter("batchSize", batchSize)
                .getResultList();
    }

    /**
     * Deletes published rows whose {@code published_at} predates {@code cutoff}, keeping the table
     * and its partial index small. Unpublished rows ({@code published_at IS NULL}) are never
     * matched, so a row that has not yet been relayed is retained regardless of age.
     *
     * @return the number of rows deleted
     */
    public long deletePublishedOlderThan(Instant cutoff) {
        return delete("publishedAt is not null and publishedAt < ?1", cutoff);
    }
}
