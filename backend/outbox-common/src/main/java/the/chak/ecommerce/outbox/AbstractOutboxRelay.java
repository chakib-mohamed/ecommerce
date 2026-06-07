package the.chak.ecommerce.outbox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.jboss.logging.Logger;

/**
 * Polling-publisher half of the transactional outbox, shared by every service. A scheduled tick
 * (and a best-effort post-commit wake-up) reads records that the write-path committed in the same
 * transaction as the business change, emits each to Kafka keyed by its aggregate id, and stamps the
 * record published once the broker acks. Delivery is at-least-once: a crash between send and stamp
 * re-sends on the next tick, which idempotent consumers absorb.
 *
 * <p>This base owns everything storage-agnostic - the coalescing {@code running} flag, the off-request
 * poll thread, the CDI JSON-B used to read stored payloads, and the batch loop with its failure
 * policy. Each service supplies the storage- and payload-specific seams ({@link #fetchBatch},
 * {@link #publish}, {@link #markPublished}, {@link #recordFailedAttempt}) and its configuration.
 *
 * <p>Concrete subclasses are the CDI beans: they must declare the {@code @Scheduled} trigger calling
 * {@link #guardedPoll()} and thin {@code @PostConstruct}/{@code @PreDestroy} hooks delegating to
 * {@link #init()}/{@link #shutdown()}.
 *
 * @param <T> the service's outbox record type
 */
public abstract class AbstractOutboxRelay<T extends OutboxRecord> {

    private static final Logger LOG = Logger.getLogger(AbstractOutboxRelay.class);

    /** How long a single send waits for the broker ack before the tick is treated as an outage. */
    protected static final long ACK_TIMEOUT_SECONDS = 30;

    /** Coalesces overlapping scheduled ticks and on-demand wake-ups into a single in-flight run. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Off-request thread for {@link #requestPoll()} so a post-commit signal never blocks the caller. */
    private ExecutorService pollExecutor;

    /** The CDI-managed JSON-B - the same configured instance the channel's {@code JsonbSerializer}
     *  uses to write the wire bytes. Reading the stored payload with it means the at-rest form and
     *  the wire form are one and the same snake_case format; there is no second serializer to keep
     *  in step. Injected into this inherited field via the concrete relay's ArC bean. */
    @Inject
    protected Jsonb jsonb;

    /** Wires the poll thread. Call from the subclass's {@code @PostConstruct}. */
    protected void init() {
        pollExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
    }

    /** Tears down the poll thread. Call from the subclass's {@code @PreDestroy}. */
    protected void shutdown() {
        pollExecutor.shutdownNow();
    }

    /** Best-effort post-commit wake-up: publish without waiting for the next scheduled tick. */
    public void requestPoll() {
        pollExecutor.execute(this::guardedPoll);
    }

    /** Runs one poll unless one is already in flight; never lets an exception escape the scheduler. */
    protected void guardedPoll() {
        if (!running.compareAndSet(false, true)) {
            return; // a poll is already in flight; it will pick up any freshly committed records
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
     * Publishes one batch of unpublished records, oldest first. Each record is sent to Kafka keyed by
     * its aggregate id; only after the broker acks is the record stamped published. A broker outage
     * aborts the whole tick so the batch is bounded to roughly one ack timeout instead of
     * {@code batchSize} of them; a poison record is retried up to {@code maxRetries} times, then
     * stamped failed and skipped forever.
     */
    public void pollAndPublish() {
        List<T> batch = fetchBatch(batchSize());
        for (T record : batch) {
            try {
                publish(record).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                markPublished(record);
            } catch (TimeoutException | ExecutionException e) {
                LOG.errorf(e, "Broker unreachable publishing outbox record id=%s; aborting this tick, "
                        + "remaining records retry on the next poll", record.recordId());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnf(e, "Interrupted awaiting broker ack for outbox record id=%s; aborting tick",
                        record.recordId());
                break;
            } catch (Exception e) {
                recordFailure(record, e);
            }
        }
    }

    /**
     * Records a per-record publish failure (poison payload / unknown topic - not a broker outage) by
     * delegating the increment-and-maybe-give-up to {@link #recordFailedAttempt}, then logging the
     * outcome from the returned attempt count. A {@code -1} means the record vanished between the
     * batch read and now (e.g. purged), so there is nothing to log.
     */
    private void recordFailure(T record, Exception cause) {
        int attempts = recordFailedAttempt(record, maxRetries());
        if (attempts < 0) {
            return;
        }
        if (attempts >= maxRetries()) {
            LOG.errorf(cause, "Giving up on outbox record id=%s after %d attempts; marking it failed "
                    + "and skipping it from now on", record.recordId(), attempts);
        } else {
            LOG.warnf(cause, "Failed to publish outbox record id=%s (attempt %d/%d); will retry",
                    record.recordId(), attempts, maxRetries());
        }
    }

    /** Fetches a batch of not-yet-published, not-yet-failed records, oldest first. */
    protected abstract List<T> fetchBatch(int batchSize);

    /**
     * Builds the Kafka message for the record (topic dispatch + payload deserialization) and sends
     * it, returning the ack future. Implementations must not block on the ack - the base awaits it
     * with {@link #ACK_TIMEOUT_SECONDS} so the broker-outage handling stays in one place. An unknown
     * topic should throw synchronously, which routes the record down the poison-retry path.
     */
    protected abstract CompletableFuture<Void> publish(T record) throws Exception;

    /** Stamps the record published so it is never re-sent. */
    protected abstract void markPublished(T record);

    /**
     * Increments the record's failed-attempt counter and, once it reaches {@code maxRetries}, stamps
     * it failed so it drops out of {@link #fetchBatch} for good. Persists the change in whatever
     * transaction model the storage needs.
     *
     * @return the new attempt count, or {@code -1} if the record no longer exists
     */
    protected abstract int recordFailedAttempt(T record, int maxRetries);

    /** Maximum records pulled per tick. */
    protected abstract int batchSize();

    /** Retry cap before a poison record is stamped failed. */
    protected abstract int maxRetries();
}
