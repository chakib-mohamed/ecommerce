package the.chak.ecommerce.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.payment.entity.OutboxEvent;

@ApplicationScoped
public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    /** See products-service's equivalent: batch fetch for the relay, oldest first, skip-locked. */
    public List<OutboxEvent> findUnpublishedForUpdate(int batchSize) {
        return getEntityManager()
                .createNativeQuery(
                        "SELECT * FROM outbox WHERE published_at IS NULL AND failed_at IS NULL "
                                + "ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
                        OutboxEvent.class)
                .setParameter("batchSize", batchSize)
                .getResultList();
    }

    public long deletePublishedOlderThan(Instant cutoff) {
        return delete("publishedAt is not null and publishedAt < ?1", cutoff);
    }
}
