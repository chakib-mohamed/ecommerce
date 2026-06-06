package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OutboxRepositoryTest {

    @Inject
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("Persists an outbox event and reads it back with a generated id and creation timestamp")
    @Transactional
    void persist_outboxEvent_isReadableWithIdAndCreatedAt() {
        // given
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType("product-updated");
        event.setTopic("product-updated");
        event.setPayload("{\"product\":{\"uuid\":\"" + aggregateId + "\"}}");

        // when
        outboxRepository.persist(event);

        // then
        OutboxEvent stored = outboxRepository.findById(event.getId());
        assertNotNull(stored.getId());
        assertNotNull(stored.getCreatedAt());
        assertNull(stored.getPublishedAt());
        assertEquals(aggregateId, stored.getAggregateId());
        assertEquals("product-updated", stored.getTopic());
    }

    @Test
    @DisplayName("The unpublished query returns rows whose published_at is null")
    @Transactional
    void findUnpublished_returnsRowsWithNullPublishedAt() {
        // given
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType("product-deleted");
        event.setTopic("product-deleted");
        event.setPayload("{\"productUuid\":\"" + aggregateId + "\"}");
        outboxRepository.persist(event);

        // when
        List<OutboxEvent> unpublished = outboxRepository.findUnpublishedForUpdate(100);

        // then
        assertTrue(unpublished.stream().anyMatch(e -> e.getId().equals(event.getId())),
                "freshly inserted, unpublished row should be returned by the relay query");
    }
}
