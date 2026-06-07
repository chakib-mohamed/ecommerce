package the.chak.ecommerce.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OutboxRepositoryTest {

    @Inject
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("Persists an outbox entry and reads it back with its id, created timestamp, and a null published timestamp")
    void persist_outboxEntry_isReadableWithIdAndCreatedAt() {
        // given
        String orderId = UUID.randomUUID().toString();
        OutboxEntry entry = newEntry(orderId);

        // when
        outboxRepository.persist(entry);

        // then
        OutboxEntry stored = outboxRepository.findById(entry.id);
        assertNotNull(stored);
        assertNotNull(stored.id);
        assertNotNull(stored.createdAt);
        assertNull(stored.publishedAt);
        assertEquals(orderId, stored.aggregateId);
        assertEquals("order-initiated", stored.topic);
    }

    @Test
    @DisplayName("The unpublished query returns entries whose published timestamp is null")
    void findUnpublished_returnsEntriesWithNullPublishedAt() {
        // given
        String orderId = UUID.randomUUID().toString();
        OutboxEntry entry = newEntry(orderId);
        outboxRepository.persist(entry);

        // when
        List<OutboxEntry> unpublished = outboxRepository.findUnpublished(100);

        // then
        assertTrue(unpublished.stream().anyMatch(e -> e.id.equals(entry.id)),
                "freshly inserted, unpublished entry should be returned by the relay query");
    }

    private OutboxEntry newEntry(String orderId) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = "order";
        entry.aggregateId = orderId;
        entry.eventType = "order-initiated";
        entry.topic = "order-initiated";
        entry.payload = "{\"id\":\"" + orderId + "\",\"userID\":\"u\",\"status\":\"INITIATED\"}";
        entry.createdAt = Instant.now();
        return entry;
    }
}
