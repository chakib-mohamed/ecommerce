package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.pricing.entity.OutboxEntry;
import the.chak.ecommerce.pricing.repository.OutboxRepository;

/**
 * Plain unit tests for the relay's failure handling: a broker outage aborts the batch without
 * burning any entry's retry budget, while a poison entry is retried up to the cap and then marked
 * failed so it is skipped forever.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayBreakTest {

    @InjectMocks
    OutboxRelay relay;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    KafkaPriceEventPublisher publisher;

    @BeforeEach
    void setUp() {
        relay.init();
        relay.batchSize = 100;
        relay.maxRetries = 5;
    }

    @AfterEach
    void tearDown() {
        relay.shutdown();
    }

    @Test
    @DisplayName("A broker outage aborts the batch after the first entry and never burns a retry")
    void brokerOutage_abortsBatch_afterFirstEntry_withoutBurningRetries() {
        // given - three healthy entries but the broker rejects every send
        List<OutboxEntry> batch = List.of(validEntry("p1"), validEntry("p2"), validEntry("p3"));
        when(outboxRepository.findUnpublished(anyInt())).thenReturn(batch);
        when(publisher.publishPriceChanged(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        // when
        relay.pollAndPublish();

        // then - only the first entry is attempted (loop breaks), nothing is stamped or counted
        verify(publisher, times(1)).publishPriceChanged(any(), any());
        verify(outboxRepository, never()).update(any(OutboxEntry.class));
        for (OutboxEntry entry : batch) {
            assertEquals(0, entry.attempts, "a broker outage must not consume an entry's retry budget");
            assertNull(entry.failedAt);
            assertNull(entry.publishedAt);
        }
    }

    @Test
    @DisplayName("A poison entry is retried up to the cap, then stamped failed and skipped")
    void poisonEntry_isRetriedUpToCap_thenMarkedFailed() {
        // given - an entry with an unknown topic that can never be published
        OutboxEntry poison = validEntry("p1");
        poison.topic = "not-a-real-topic";
        when(outboxRepository.findUnpublished(anyInt())).thenReturn(List.of(poison));

        // when - the relay ticks repeatedly
        for (int attempt = 1; attempt <= 5; attempt++) {
            relay.pollAndPublish();

            // then - each tick increments the counter; failedAt is stamped only at the cap
            assertEquals(attempt, poison.attempts);
            if (attempt < 5) {
                assertNull(poison.failedAt, "must not give up before the retry cap is reached");
            }
        }
        assertNotNull(poison.failedAt, "the entry must be stamped failed once the cap is reached");

        // and - a poison entry never reaches the broker, and every failed attempt is persisted
        verify(publisher, never()).publishPriceChanged(any(), any());
        verify(outboxRepository, times(5)).update(poison);
    }

    private OutboxEntry validEntry(String productId) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = "price";
        entry.aggregateId = productId;
        entry.eventType = "price-changed";
        entry.topic = "price-changed";
        entry.payload = "{\"productId\":\"" + productId + "\",\"newPrice\":9.99}";
        entry.createdAt = Instant.now();
        return entry;
    }
}
