package the.chak.ecommerce.outbox;

/**
 * The read view of a transactional-outbox record that {@link AbstractOutboxRelay} needs while
 * draining a batch. Concrete entities (a Mongo document or a JPA row) implement this so the shared
 * relay loop can log and key a record without knowing its storage type.
 *
 * <p>Deliberately minimal: the {@code topic} and {@code payload} are not exposed here because they
 * are consumed only inside each service's {@code publish} override, which already holds the concrete
 * entity type and its event-payload classes.
 */
public interface OutboxRecord {

    /** Stable identifier of this record, used purely in relay log messages. */
    Object recordId();

    /** The Kafka message key (the aggregate id) for per-aggregate ordering. */
    String aggregateKey();
}
