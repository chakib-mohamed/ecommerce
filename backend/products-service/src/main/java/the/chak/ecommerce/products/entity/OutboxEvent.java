package the.chak.ecommerce.products.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import the.chak.ecommerce.outbox.OutboxRecord;

@Getter
@Setter
@Entity
@Table(name = "outbox")
public class OutboxEvent implements OutboxRecord {

    @Id
    private UUID id;

    private String aggregateType;

    private UUID aggregateId;

    private String eventType;

    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    private Instant createdAt;

    private Instant publishedAt;

    /** Count of failed per-row publish attempts (poison payload / unknown topic); broker outages
     *  do not increment it. Once it reaches the relay's retry cap the row is stamped failed. */
    private int attempts;

    /** Set when the row exhausts its retry cap; a non-null value excludes it from the relay so a
     *  poison row never blocks or re-burns the relay. Kept in-table for inspection. */
    private Instant failedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public Object recordId() {
        return id;
    }

    @Override
    public String aggregateKey() {
        return aggregateId.toString();
    }
}
