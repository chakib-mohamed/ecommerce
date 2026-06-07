package the.chak.ecommerce.pricing.entity;

import java.time.Instant;
import java.util.UUID;
import io.quarkus.mongodb.panache.common.MongoEntity;
import the.chak.ecommerce.outbox.OutboxRecord;

/**
 * Transactional-outbox document. Written to the {@code outbox} collection in the same Mongo
 * transaction as the business price write, then drained to Kafka by the
 * {@code the.chak.ecommerce.pricing.control.OutboxRelay}. {@code publishedAt} is {@code null}
 * until the broker acks; the relay stamps it on success.
 */
@MongoEntity(collection = "outbox")
public class OutboxEntry implements OutboxRecord {

    /** Event id, mapped to {@code _id}; also the Kafka dedup handle for idempotent consumers. */
    public UUID id;

    public String aggregateType;

    /** The product id - used as the Kafka message key for per-aggregate ordering. */
    public String aggregateId;

    public String eventType;

    public String topic;

    /** JSON-B-serialized event body (camelCase); re-serialized to the wire by the channel serializer. */
    public String payload;

    public Instant createdAt;

    public Instant publishedAt;

    /** Count of failed per-row publish attempts (poison payload / unknown topic); broker outages
     *  do not increment it. Once it reaches the relay's retry cap the entry is stamped failed. */
    public int attempts;

    /** Set when the entry exhausts its retry cap; a non-null value excludes it from the relay so a
     *  poison entry never blocks or re-burns the relay. Kept in the collection for inspection. */
    public Instant failedAt;

    @Override
    public Object recordId() {
        return id;
    }

    @Override
    public String aggregateKey() {
        return aggregateId;
    }
}
