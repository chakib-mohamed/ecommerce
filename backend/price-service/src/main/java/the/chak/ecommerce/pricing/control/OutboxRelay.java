package the.chak.ecommerce.pricing.control;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.entity.OutboxEntry;
import the.chak.ecommerce.pricing.repository.OutboxRepository;

/**
 * Polling-publisher half of the transactional outbox. A scheduled tick (and a best-effort
 * post-commit wake-up from {@code PriceService}) reads entries that the write-path committed in the
 * same Mongo transaction as the business change, emits each to Kafka keyed by its product id, and
 * stamps {@code publishedAt} once the broker acks. Delivery is at-least-once: a crash between send
 * and stamp re-sends on the next tick, which idempotent consumers absorb.
 */
@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);
    private static final long ACK_TIMEOUT_SECONDS = 30;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    KafkaPriceEventPublisher publisher;

    @ConfigProperty(name = "pricing.outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "pricing.outbox.max-retries", defaultValue = "5")
    int maxRetries;

    /** Coalesces overlapping scheduled ticks and on-demand wake-ups into a single in-flight run. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Off-request thread for {@link #requestPoll()} so a post-commit signal never blocks the caller. */
    private ExecutorService pollExecutor;

    /** Plain (camelCase) JSON-B used only to read the stored payload back into an event object;
     *  it matches {@link OutboxEventFactory}'s writer. The Kafka wire format is decided later by the
     *  channel's {@code JsonbSerializer} (CDI-managed JSON-B) and is snake_case. */
    private Jsonb jsonb;

    @PostConstruct
    void init() {
        pollExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
        jsonb = JsonbBuilder.create();
    }

    @PreDestroy
    void shutdown() {
        pollExecutor.shutdownNow();
        try {
            jsonb.close();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to close JSON-B");
        }
    }

    @Scheduled(every = "{pricing.outbox.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPoll() {
        guardedPoll();
    }

    /** Best-effort post-commit wake-up: publish without waiting for the next scheduled tick. */
    public void requestPoll() {
        pollExecutor.execute(this::guardedPoll);
    }

    private void guardedPoll() {
        if (!running.compareAndSet(false, true)) {
            return; // a poll is already in flight; it will pick up any freshly committed entries
        }
        try {
            pollAndPublish();
        } catch (Exception e) {
            LOG.errorf(e, "Outbox poll failed");
        } finally {
            running.set(false);
        }
    }

    /**
     * Publishes one batch of unpublished entries, oldest first. Each entry is sent to Kafka keyed by
     * its product id; only after the broker acks is {@code publishedAt} stamped (a single-document
     * update - no transaction needed). A broker outage aborts the whole tick so the batch is bounded
     * to roughly one ack timeout instead of {@code batchSize} of them; a poison entry is retried up
     * to {@code maxRetries} times, then stamped {@code failedAt} and skipped forever.
     */
    public void pollAndPublish() {
        List<OutboxEntry> batch = outboxRepository.findUnpublished(batchSize);
        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                stampPublished(entry);
            } catch (TimeoutException | ExecutionException e) {
                LOG.errorf(e, "Broker unreachable publishing outbox entry id=%s; aborting this tick, "
                        + "remaining entries retry on the next poll", entry.id);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnf(e, "Interrupted awaiting broker ack for outbox entry id=%s; aborting tick",
                        entry.id);
                break;
            } catch (Exception e) {
                recordFailedAttempt(entry, e);
            }
        }
    }

    /**
     * Records a per-entry publish failure (poison payload / unknown topic - not a broker outage):
     * increments the attempt counter and, once it reaches {@code maxRetries}, stamps {@code failedAt}
     * so the entry drops out of {@link OutboxRepository#findUnpublished} for good. Persisted as a
     * single-document update; the next entry in the batch is still attempted.
     */
    private void recordFailedAttempt(OutboxEntry entry, Exception cause) {
        entry.attempts++;
        if (entry.attempts >= maxRetries) {
            entry.failedAt = Instant.now();
            LOG.errorf(cause, "Giving up on outbox entry id=%s after %d attempts; marking it failed "
                    + "and skipping it from now on", entry.id, entry.attempts);
        } else {
            LOG.warnf(cause, "Failed to publish outbox entry id=%s (attempt %d/%d); will retry",
                    entry.id, entry.attempts, maxRetries);
        }
        outboxRepository.update(entry);
    }

    private void publish(OutboxEntry entry) throws Exception {
        if (!"price-changed".equals(entry.topic)) {
            throw new IllegalStateException("Unknown outbox topic: " + entry.topic);
        }
        PriceChangedEvent payload = jsonb.fromJson(entry.payload, PriceChangedEvent.class);
        CompletableFuture<Void> ack = publisher.publishPriceChanged(payload, entry.aggregateId);
        ack.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void stampPublished(OutboxEntry entry) {
        entry.publishedAt = Instant.now();
        outboxRepository.update(entry);
    }
}
