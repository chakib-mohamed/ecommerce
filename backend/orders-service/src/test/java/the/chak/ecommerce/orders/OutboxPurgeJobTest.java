package the.chak.ecommerce.orders;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.control.OutboxPurgeJob;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OutboxPurgeJobTest {

    @Inject
    OutboxPurgeJob purgeJob;

    @Inject
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("Purge deletes only published entries older than the retention window, keeping recent and unpublished entries")
    void purge_deletesOnlyOldPublishedEntries_keepsRecentAndUnpublished() {
        // given - one old-published entry, one recent-published entry, one unpublished entry.
        // Retention defaults to 7 days, so "old" is well beyond it and "recent" is well inside it.
        UUID oldPublishedId = insert(Instant.now().minus(Duration.ofDays(30)), null);
        UUID recentPublishedId = insert(Instant.now().minus(Duration.ofMinutes(5)), null);
        UUID unpublishedId = insert(null, null);

        // when
        purgeJob.purgeOldPublished();

        // then - only the old published entry is gone; the recent and the unpublished entries survive.
        assertNull(outboxRepository.findById(oldPublishedId), "a published entry older than the retention window must be purged");
        assertNotNull(outboxRepository.findById(recentPublishedId), "a recently published entry must be kept");
        assertNotNull(outboxRepository.findById(unpublishedId), "an unpublished entry must never be purged");
    }

    @Test
    @DisplayName("Purge never deletes an unpublished entry even when it is older than the retention window")
    void purge_neverDeletesUnpublished_evenWhenOld() {
        // given - an unpublished entry whose createdAt is far in the past but publishedAt is still null
        UUID id = insert(null, Instant.now().minus(Duration.ofDays(365)));

        // when
        purgeJob.purgeOldPublished();

        // then
        assertNotNull(outboxRepository.findById(id), "an unpublished entry must survive regardless of age");
    }

    // --- helpers -----------------------------------------------------------

    private UUID insert(Instant publishedAt, Instant createdAt) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = "order";
        entry.aggregateId = UUID.randomUUID().toString();
        entry.eventType = "order-initiated";
        entry.topic = "order-initiated";
        entry.payload = "{\"id\":\"" + entry.aggregateId + "\",\"userID\":\"u\",\"status\":\"INITIATED\"}";
        entry.createdAt = createdAt != null ? createdAt : Instant.now();
        entry.publishedAt = publishedAt;
        outboxRepository.persist(entry);
        return entry.id;
    }
}
