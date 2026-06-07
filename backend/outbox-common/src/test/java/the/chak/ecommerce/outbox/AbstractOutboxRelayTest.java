package the.chak.ecommerce.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Storage-agnostic proof of the relay's batch loop and failure policy, exercised once here instead
 * of in every service: a broker outage aborts the tick without burning any record's retry budget,
 * a poison record is retried up to the cap and then given up on, and a healthy record is stamped
 * published. Uses a hand-rolled fake subclass so no broker or database is involved.
 */
class AbstractOutboxRelayTest {

    private TestRelay relay;

    @BeforeEach
    void setUp() {
        relay = new TestRelay();
        relay.init();
    }

    @AfterEach
    void tearDown() {
        relay.shutdown();
    }

    @Test
    @DisplayName("A broker outage aborts the batch after the first record and records no failures")
    void brokerOutage_abortsBatch_afterFirstRecord_withoutRecordingFailures() {
        relay.batch = List.of(
                new FakeRecord("r1", Mode.BROKER_DOWN),
                new FakeRecord("r2", Mode.BROKER_DOWN),
                new FakeRecord("r3", Mode.BROKER_DOWN));

        relay.pollAndPublish();

        assertEquals(1, relay.publishCalls, "the loop must break after the first failed send");
        assertTrue(relay.published.isEmpty(), "nothing is stamped published on a broker outage");
        assertTrue(relay.failureCalls.isEmpty(),
                "a broker outage must not consume any record's retry budget");
    }

    @Test
    @DisplayName("A poison record is retried up to the cap, then given up on, and never republished")
    void poisonRecord_isRetriedUpToCap_thenGivenUp() {
        relay.maxRetries = 5;
        FakeRecord poison = new FakeRecord("r1", Mode.POISON);
        relay.batch = List.of(poison);

        for (int attempt = 1; attempt <= 5; attempt++) {
            relay.pollAndPublish();
            assertEquals(attempt, relay.attempts.get(poison),
                    "each tick increments the poison record's attempt counter");
            if (attempt < 5) {
                assertFalse(poison.failed, "must not give up before the retry cap is reached");
            }
        }

        assertTrue(poison.failed, "the record is stamped failed once the cap is reached");
        assertTrue(relay.published.isEmpty(), "a poison record never reaches the broker");
    }

    @Test
    @DisplayName("A healthy record is sent and stamped published")
    void healthyRecord_isPublished_andStamped() {
        FakeRecord ok = new FakeRecord("r1", Mode.SUCCESS);
        relay.batch = List.of(ok);

        relay.pollAndPublish();

        assertEquals(List.of(ok), relay.published, "a healthy record is stamped published");
        assertTrue(relay.failureCalls.isEmpty(), "a healthy record records no failure");
    }

    // --- fakes -------------------------------------------------------------

    private enum Mode { SUCCESS, BROKER_DOWN, POISON }

    private static final class FakeRecord implements OutboxRecord {
        private final String id;
        private final Mode mode;
        private boolean failed;

        FakeRecord(String id, Mode mode) {
            this.id = id;
            this.mode = mode;
        }

        @Override
        public Object recordId() {
            return id;
        }

        @Override
        public String aggregateKey() {
            return id;
        }
    }

    private static final class TestRelay extends AbstractOutboxRelay<FakeRecord> {
        private List<FakeRecord> batch = new ArrayList<>();
        private int maxRetries = 5;
        private int publishCalls;
        private final List<FakeRecord> published = new ArrayList<>();
        private final List<FakeRecord> failureCalls = new ArrayList<>();
        private final Map<FakeRecord, Integer> attempts = new HashMap<>();

        @Override
        protected List<FakeRecord> fetchBatch(int batchSize) {
            return batch;
        }

        @Override
        protected CompletableFuture<Void> publish(FakeRecord record) {
            publishCalls++;
            return switch (record.mode) {
                case SUCCESS -> CompletableFuture.completedFuture(null);
                case BROKER_DOWN -> CompletableFuture.failedFuture(new RuntimeException("broker down"));
                case POISON -> throw new IllegalStateException("unknown topic");
            };
        }

        @Override
        protected void markPublished(FakeRecord record) {
            published.add(record);
        }

        @Override
        protected int recordFailedAttempt(FakeRecord record, int cap) {
            failureCalls.add(record);
            int n = attempts.merge(record, 1, Integer::sum);
            if (n >= cap) {
                record.failed = true;
            }
            return n;
        }

        @Override
        protected int batchSize() {
            return 100;
        }

        @Override
        protected int maxRetries() {
            return maxRetries;
        }
    }
}
