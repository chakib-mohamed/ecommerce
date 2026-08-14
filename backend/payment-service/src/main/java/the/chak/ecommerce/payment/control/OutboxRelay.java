package the.chak.ecommerce.payment.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import io.opentelemetry.context.Context;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.outbox.AbstractOutboxRelay;
import the.chak.ecommerce.outbox.OutboxTracing;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;
import the.chak.ecommerce.payment.entity.OutboxEvent;
import the.chak.ecommerce.payment.repository.OutboxRepository;

/**
 * Payment-specific outbox relay. Scheduling, batching and at-least-once failure handling live in
 * {@link AbstractOutboxRelay}; this subclass supplies the JTA-bounded fetch, the publish, and the
 * stamping done in separate short transactions with no I/O inside them.
 *
 * <p>Without this the replies would be written and never sent, and every order would wait on a
 * payment that had already happened.
 */
@ApplicationScoped
public class OutboxRelay extends AbstractOutboxRelay<OutboxEvent> {

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    KafkaEventPublisher kafkaEventPublisher;

    @ConfigProperty(name = "payment.outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "payment.outbox.max-retries", defaultValue = "5")
    int maxRetries;

    @PostConstruct
    void onStart() {
        init();
    }

    @PreDestroy
    void onStop() {
        shutdown();
    }

    @Scheduled(every = "{payment.outbox.poll-interval}",
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
        Context parent = OutboxTracing.extract(event.getTraceparent());
        return switch (event.getTopic()) {
            case "payment-captured" -> kafkaEventPublisher.publishPaymentCaptured(
                    jsonb.fromJson(event.getPayload(), PaymentCapturedEvent.class), key, parent);
            case "payment-failed" -> kafkaEventPublisher.publishPaymentFailed(
                    jsonb.fromJson(event.getPayload(), PaymentFailedEvent.class), key, parent);
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
