package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.control.KafkaEventPublisher;
import the.chak.ecommerce.products.control.OutboxRelay;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Integration tests for the relay's failure handling: a broker outage aborts the batch without
 * burning any row's retry budget, while a poison row is retried up to the cap, then stamped failed
 * and excluded from future batches by the {@code findUnpublishedForUpdate} query.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OutboxRelayBreakTest {

    @Inject
    OutboxRelay relay;

    @Inject
    OutboxRepository outboxRepository;

    @InjectMock
    KafkaEventPublisher publisher;

    @Test
    @DisplayName("A broker outage aborts the batch after the first row and never burns a retry")
    void brokerOutage_abortsBatch_afterFirstRow_withoutBurningRetries() {
        // given - a broker that rejects every send, and three healthy unpublished rows
        when(publisher.publishProductUpdated(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        UUID id1 = insertUnpublished("product-updated", UUID.randomUUID());
        UUID id2 = insertUnpublished("product-updated", UUID.randomUUID());
        UUID id3 = insertUnpublished("product-updated", UUID.randomUUID());

        // when
        relay.pollAndPublish();

        // then - only the first row is attempted (loop breaks); none stamped or counted
        verify(publisher, times(1)).publishProductUpdated(any(), any(), any());
        for (UUID id : List.of(id1, id2, id3)) {
            OutboxEvent reloaded = findById(id);
            assertNull(reloaded.getPublishedAt());
            assertNull(reloaded.getFailedAt());
            assertEquals(0, reloaded.getAttempts(), "a broker outage must not consume a row's retry budget");
        }
    }

    @Test
    @DisplayName("A poison row is retried up to the cap, then stamped failed and excluded from the relay")
    void poisonRow_isRetriedUpToCap_thenMarkedFailed_andExcluded() {
        // given - a row whose topic can never be published
        UUID rowId = insertUnpublished("not-a-real-topic", UUID.randomUUID());

        // when - the relay ticks repeatedly
        for (int attempt = 1; attempt <= 5; attempt++) {
            relay.pollAndPublish();

            // then - each tick increments the counter; failedAt is stamped only at the cap
            OutboxEvent reloaded = findById(rowId);
            assertEquals(attempt, reloaded.getAttempts());
            if (attempt < 5) {
                assertNull(reloaded.getFailedAt(), "must not give up before the retry cap is reached");
            }
        }

        // and - the row is stamped failed and the relay query no longer returns it
        OutboxEvent reloaded = findById(rowId);
        assertNotNull(reloaded.getFailedAt(), "the row must be stamped failed once the cap is reached");
        List<OutboxEvent> batch = QuarkusTransaction.requiringNew()
                .call(() -> outboxRepository.findUnpublishedForUpdate(100));
        assertTrue(batch.stream().noneMatch(e -> e.getId().equals(rowId)),
                "a failed row must be excluded from future relay batches");
    }

    // --- helpers -----------------------------------------------------------

    private UUID insertUnpublished(String topic, UUID aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload("{\"product\":{\"uuid\":\"" + aggregateId + "\"}}");
        QuarkusTransaction.requiringNew().run(() -> outboxRepository.persist(event));
        return event.getId();
    }

    private OutboxEvent findById(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> outboxRepository.findById(id));
    }
}
