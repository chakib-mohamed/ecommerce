package the.chak.ecommerce.products.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
     * transaction with no I/O inside it. Un-acked rows stay {@code NULL} for the next tick.
     */
    public void pollAndPublish() {
        List<OutboxEvent> batch = QuarkusTransaction.requiringNew()
                .call(() -> outboxRepository.findUnpublishedForUpdate(batchSize));
        for (OutboxEvent event : batch) {
            try {
                publish(event);
                UUID id = event.getId();
                QuarkusTransaction.requiringNew().run(() -> stampPublished(id));
            } catch (Exception e) {
                LOG.errorf(e, "Failed to publish outbox row id=%s; leaving it for the next tick",
                        event.getId());
            }
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
