package the.chak.ecommerce.products.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Polling-publisher half of the transactional outbox. A scheduled tick (and a best-effort
 * post-commit wake-up) reads rows that the write-path committed in the same transaction as the
 * business change, emits each to Kafka keyed by its aggregate id, and stamps {@code published_at}
 * once the broker acks. Delivery is at-least-once: a crash between send and stamp re-sends on the
 * next tick, which idempotent consumers absorb.
 */
@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);
    private static final long ACK_TIMEOUT_SECONDS = 30;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @ConfigProperty(name = "products.outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "products.outbox.max-retries", defaultValue = "5")
    int maxRetries;

    /** Coalesces overlapping scheduled ticks and on-demand wake-ups into a single in-flight run. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Off-request thread for {@link #requestPoll()} so a post-commit signal never blocks the caller. */
    private ExecutorService pollExecutor;

    /** Plain (camelCase) JSON-B used only to read the DB-stored payload back into an event object;
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

    @Scheduled(every = "{products.outbox.poll-interval}",
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
            return; // a poll is already in flight; it will pick up any freshly committed rows
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
     * Publishes one batch of unpublished rows, oldest first. Each row is sent to Kafka keyed by its
     * aggregate id; only after the broker acks is {@code published_at} stamped, in a short separate
     * transaction with no I/O inside it. A broker outage aborts the whole tick so the batch is
     * bounded to roughly one ack timeout instead of {@code batchSize} of them; a poison row is
     * retried up to {@code maxRetries} times, then stamped {@code failed_at} and skipped forever.
     */
    public void pollAndPublish() {
        List<OutboxEvent> batch = QuarkusTransaction.requiringNew()
                .call(() -> outboxRepository.findUnpublishedForUpdate(batchSize));
        for (OutboxEvent event : batch) {
            try {
                publish(event);
                UUID id = event.getId();
                QuarkusTransaction.requiringNew().run(() -> stampPublished(id));
            } catch (TimeoutException | ExecutionException e) {
                LOG.errorf(e, "Broker unreachable publishing outbox row id=%s; aborting this tick, "
                        + "remaining rows retry on the next poll", event.getId());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnf(e, "Interrupted awaiting broker ack for outbox row id=%s; aborting tick",
                        event.getId());
                break;
            } catch (Exception e) {
                recordFailedAttempt(event.getId(), e);
            }
        }
    }

    /**
     * Records a per-row publish failure (poison payload / unknown topic - not a broker outage):
     * increments the attempt counter and, once it reaches {@code maxRetries}, stamps
     * {@code failed_at} so the row drops out of {@link OutboxRepository#findUnpublishedForUpdate}
     * for good. Run in its own short transaction loading the managed entity (same pattern as
     * {@link #stampPublished}); the next row in the batch is still attempted.
     */
    private void recordFailedAttempt(UUID id, Exception cause) {
        int attempts = QuarkusTransaction.requiringNew().call(() -> {
            OutboxEvent managed = outboxRepository.findById(id);
            if (managed == null) {
                return -1;
            }
            managed.setAttempts(managed.getAttempts() + 1);
            if (managed.getAttempts() >= maxRetries) {
                managed.setFailedAt(Instant.now());
            }
            return managed.getAttempts();
        });
        if (attempts < 0) {
            return; // row vanished (e.g. purged) between the batch read and now
        }
        if (attempts >= maxRetries) {
            LOG.errorf(cause, "Giving up on outbox row id=%s after %d attempts; marking it failed "
                    + "and skipping it from now on", id, attempts);
        } else {
            LOG.warnf(cause, "Failed to publish outbox row id=%s (attempt %d/%d); will retry",
                    id, attempts, maxRetries);
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        String key = event.getAggregateId().toString();
        CompletableFuture<Void> ack;

        switch (event.getTopic()) {
            case "product-updated" -> {
                ProductUpdatedEvent payload =
                        jsonb.fromJson(event.getPayload(), ProductUpdatedEvent.class);
                ack = kafkaEventPublisher.publishProductUpdated(payload, key);
            }
            case "product-deleted" -> {
                ProductDeletedEvent payload =
                        jsonb.fromJson(event.getPayload(), ProductDeletedEvent.class);
                ack = kafkaEventPublisher.publishProductDeleted(payload, key);
            }
            default -> throw new IllegalStateException("Unknown outbox topic: " + event.getTopic());
        }
        ack.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void stampPublished(UUID id) {
        OutboxEvent managed = outboxRepository.findById(id);
        if (managed != null) {
            managed.setPublishedAt(Instant.now());
        }
    }
}
