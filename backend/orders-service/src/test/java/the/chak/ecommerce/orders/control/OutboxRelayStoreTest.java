package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * Plain unit tests for the order relay's Mongo storage glue. The shared batch loop and broker-outage
 * policy are proven once in {@code AbstractOutboxRelayTest}; here we only check that this service
 * stamps a single document correctly: published on success, and failed once the retry cap is hit.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayStoreTest {

    @InjectMocks
    OutboxRelay relay;

    @Mock
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("markPublished stamps the published timestamp and persists the document")
    void markPublished_stampsTimestamp_andPersists() {
        OutboxEntry entry = entry();

        relay.markPublished(entry);

        assertNotNull(entry.publishedAt, "a successful send must stamp the published timestamp");
        verify(outboxRepository).update(entry);
    }

    @Test
    @DisplayName("recordFailedAttempt below the cap increments attempts without stamping failed")
    void recordFailedAttempt_belowCap_incrementsOnly() {
        OutboxEntry entry = entry();

        int attempts = relay.recordFailedAttempt(entry, 5);

        assertEquals(1, attempts);
        assertEquals(1, entry.attempts);
        assertNull(entry.failedAt, "must not give up before the retry cap is reached");
        verify(outboxRepository).update(entry);
    }

    @Test
    @DisplayName("recordFailedAttempt at the cap stamps the entry failed so the relay skips it")
    void recordFailedAttempt_atCap_stampsFailed() {
        OutboxEntry entry = entry();
        entry.attempts = 4;

        int attempts = relay.recordFailedAttempt(entry, 5);

        assertEquals(5, attempts);
        assertNotNull(entry.failedAt, "the entry must be stamped failed once the cap is reached");
        verify(outboxRepository).update(entry);
    }

    private OutboxEntry entry() {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = "order";
        entry.aggregateId = "o1";
        entry.eventType = "order-initiated";
        entry.topic = "order-initiated";
        entry.payload = "{\"id\":\"o1\"}";
        entry.createdAt = Instant.now();
        return entry;
    }
}
