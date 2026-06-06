package the.chak.ecommerce.pricing.control;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
     * update - no transaction needed). Un-acked entries stay {@code null} for the next tick.
     */
    public void pollAndPublish() {
        List<OutboxEntry> batch = outboxRepository.findUnpublished(batchSize);
        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                stampPublished(entry);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to publish outbox entry id=%s; leaving it for the next tick",
                        entry.id);
            }
        }
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
