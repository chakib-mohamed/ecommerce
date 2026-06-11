package the.chak.ecommerce.orders.control;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.opentelemetry.context.Context;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OutboxRepository;
import the.chak.ecommerce.outbox.AbstractOutboxRelay;
import the.chak.ecommerce.outbox.OutboxTracing;

/**
 * Order-specific outbox relay. All scheduling, batching, and at-least-once failure handling live in
 * {@link AbstractOutboxRelay}; this subclass supplies only the Mongo fetch, the {@code order-initiated}
 * publish, and the single-document stamping (no transaction needed for a single Mongo write).
 */
@ApplicationScoped
public class OutboxRelay extends AbstractOutboxRelay<OutboxEntry> {

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    KafkaOrderEventPublisher publisher;

    @ConfigProperty(name = "orders.outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "orders.outbox.max-retries", defaultValue = "5")
    int maxRetries;

    @PostConstruct
    void onStart() {
        init();
    }

    @PreDestroy
    void onStop() {
        shutdown();
    }

    @Scheduled(every = "{orders.outbox.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPoll() {
        guardedPoll();
    }

    @Override
    protected List<OutboxEntry> fetchBatch(int size) {
        return outboxRepository.findUnpublished(size);
    }

    @Override
    protected CompletableFuture<Void> publish(OutboxEntry entry) {
        if (!"order-initiated".equals(entry.topic)) {
            throw new IllegalStateException("Unknown outbox topic: " + entry.topic);
        }
        OrderDTO payload = jsonb.fromJson(entry.payload, OrderDTO.class);
        Context parent = OutboxTracing.extract(entry.traceparent);
        return publisher.publishOrderInitiated(payload, entry.aggregateKey(), parent);
    }

    @Override
    protected void markPublished(OutboxEntry entry) {
        entry.publishedAt = Instant.now();
        outboxRepository.update(entry);
    }

    @Override
    protected int recordFailedAttempt(OutboxEntry entry, int cap) {
        entry.attempts++;
        if (entry.attempts >= cap) {
            entry.failedAt = Instant.now();
        }
        outboxRepository.update(entry);
        return entry.attempts;
    }

    @Override
    protected int batchSize() {
        return batchSize;
    }

    @Override
    protected int maxRetries() {
        return maxRetries;
    }
}
