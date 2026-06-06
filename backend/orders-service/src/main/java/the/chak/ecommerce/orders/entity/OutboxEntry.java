package the.chak.ecommerce.orders.entity;

import java.time.Instant;
import java.util.UUID;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Transactional-outbox document. Written to the {@code outbox} collection in the same Mongo
 * transaction as the business order write, then drained to Kafka by the
 * {@code the.chak.ecommerce.orders.control.OutboxRelay}. {@code publishedAt} is {@code null}
 * until the broker acks; the relay stamps it on success.
 */
@MongoEntity(collection = "outbox")
public class OutboxEntry {

    /** Event id, mapped to {@code _id}; also the Kafka dedup handle for idempotent consumers. */
    public UUID id;

    public String aggregateType;

    /** The order id - used as the Kafka message key for per-aggregate ordering. */
    public String aggregateId;

    public String eventType;

    public String topic;

    /** JSON-B-serialized event body (camelCase); re-serialized to the wire by the channel serializer. */
    public String payload;

    public Instant createdAt;

    public Instant publishedAt;
}
