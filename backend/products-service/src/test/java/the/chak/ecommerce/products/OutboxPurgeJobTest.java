package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.control.OutboxPurgeJob;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OutboxPurgeJobTest {

    @Inject
    OutboxPurgeJob purgeJob;

    @Inject
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("Purge deletes only published rows older than the retention window, keeping recent and unpublished rows")
    void purge_deletesOnlyOldPublishedRows_keepsRecentAndUnpublished() {
        // given - one old-published row, one recent-published row, one unpublished row.
        // Retention defaults to 7 days, so "old" is well beyond it and "recent" is well inside it.
        UUID oldPublishedId = insert("product-updated", publishedAt(Instant.now().minus(Duration.ofDays(30))));
        UUID recentPublishedId = insert("product-updated", publishedAt(Instant.now().minus(Duration.ofMinutes(5))));
        UUID unpublishedId = insert("product-deleted", null);

        // when
        purgeJob.purgeOldPublished();

        // then - only the old published row is gone; the recent and the unpublished rows survive.
        assertNull(findById(oldPublishedId), "a published row older than the retention window must be purged");
        assertNotNull(findById(recentPublishedId), "a recently published row must be kept");
        assertNotNull(findById(unpublishedId), "an unpublished row must never be purged");
    }

    @Test
    @DisplayName("Purge never deletes an unpublished row even when it is older than the retention window")
    void purge_neverDeletesUnpublished_evenWhenOld() {
        // given - an unpublished row whose created_at is far in the past but published_at is still null
        UUID id = insert("product-updated", null, Instant.now().minus(Duration.ofDays(365)));

        // when
        purgeJob.purgeOldPublished();

        // then
        assertNotNull(findById(id), "an unpublished row must survive regardless of age");
    }

    // --- helpers -----------------------------------------------------------

    private Instant publishedAt(Instant when) {
        return when;
    }

    private UUID insert(String topic, Instant publishedAt) {
        return insert(topic, publishedAt, null);
    }

    private UUID insert(String topic, Instant publishedAt, Instant createdAt) {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload("{\"product\":{\"uuid\":\"" + aggregateId + "\"}}");
        if (createdAt != null) {
            event.setCreatedAt(createdAt);
        }
        if (publishedAt != null) {
            event.setPublishedAt(publishedAt);
        }
        QuarkusTransaction.requiringNew().run(() -> outboxRepository.persist(event));
        return event.getId();
    }

    private OutboxEvent findById(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> outboxRepository.findById(id));
    }
}
