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
     * Claims a batch of not-yet-published rows for the relay, oldest first.
     * {@code FOR UPDATE SKIP LOCKED} lets multiple service instances run the relay
     * concurrently without ever claiming the same row twice.
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
