package the.chak.ecommerce.products.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.outbox.AbstractOutboxRelay;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Product-specific outbox relay. All scheduling, batching, and at-least-once failure handling live
 * in {@link AbstractOutboxRelay}; this subclass supplies the JTA-bounded fetch
 * ({@code FOR UPDATE SKIP LOCKED} in its own short transaction), the {@code product-updated} /
 * {@code product-deleted} publish, and the stamping done in separate short transactions with no I/O
 * inside them.
 */
@ApplicationScoped
public class OutboxRelay extends AbstractOutboxRelay<OutboxEvent> {

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @ConfigProperty(name = "products.outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "products.outbox.max-retries", defaultValue = "5")
    int maxRetries;

    @PostConstruct
    void onStart() {
        init();
    }

    @PreDestroy
    void onStop() {
        shutdown();
    }

    @Scheduled(every = "{products.outbox.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPoll() {
        guardedPoll();
    }

    @Override
    protected List<OutboxEvent> fetchBatch(int size) {
        return QuarkusTransaction.requiringNew()
                .call(() -> outboxRepository.findUnpublishedForUpdate(size));
    }

    @Override
    protected CompletableFuture<Void> publish(OutboxEvent event) {
        String key = event.aggregateKey();
        return switch (event.getTopic()) {
            case "product-updated" -> {
                ProductUpdatedEvent payload =
                        jsonb.fromJson(event.getPayload(), ProductUpdatedEvent.class);
                yield kafkaEventPublisher.publishProductUpdated(payload, key);
            }
            case "product-deleted" -> {
                ProductDeletedEvent payload =
                        jsonb.fromJson(event.getPayload(), ProductDeletedEvent.class);
                yield kafkaEventPublisher.publishProductDeleted(payload, key);
            }
            default -> throw new IllegalStateException("Unknown outbox topic: " + event.getTopic());
        };
    }

    @Override
    protected void markPublished(OutboxEvent event) {
        UUID id = event.getId();
        QuarkusTransaction.requiringNew().run(() -> stampPublished(id));
    }

    @Override
    protected int recordFailedAttempt(OutboxEvent event, int cap) {
        UUID id = event.getId();
        return QuarkusTransaction.requiringNew().call(() -> {
            OutboxEvent managed = outboxRepository.findById(id);
            if (managed == null) {
                return -1; // row vanished (e.g. purged) between the batch read and now
            }
            managed.setAttempts(managed.getAttempts() + 1);
            if (managed.getAttempts() >= cap) {
                managed.setFailedAt(Instant.now());
            }
            return managed.getAttempts();
        });
    }

    @Override
    protected int batchSize() {
        return batchSize;
    }

    @Override
    protected int maxRetries() {
        return maxRetries;
    }

    private void stampPublished(UUID id) {
        OutboxEvent managed = outboxRepository.findById(id);
        if (managed != null) {
            managed.setPublishedAt(Instant.now());
        }
    }
}
