package the.chak.ecommerce.orders.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.entity.OutboxEntry;

@ApplicationScoped
public class OutboxRepository implements PanacheMongoRepositoryBase<OutboxEntry, UUID> {

    /**
     * Returns a batch of not-yet-published entries for the relay, oldest first. Mongo has no
     * {@code FOR UPDATE SKIP LOCKED}; cross-instance duplicates are absorbed by the idempotent
     * order-initiated consumer and broker idempotence (at-least-once delivery).
     */
    public List<OutboxEntry> findUnpublished(int batchSize) {
        return find("{'publishedAt': null}", Sort.ascending("createdAt"))
                .page(0, batchSize)
                .list();
    }

    /**
     * Deletes published entries whose {@code publishedAt} predates {@code cutoff}, keeping the
     * collection small. The {@code $lt} comparison against a date never matches a {@code null}
     * {@code publishedAt}, so entries not yet relayed are retained regardless of age.
     *
     * @return the number of entries deleted
     */
    public long deletePublishedOlderThan(Instant cutoff) {
        return delete("{'publishedAt': {$ne: null, $lt: :cutoff}}",
                Parameters.with("cutoff", cutoff));
    }
}
