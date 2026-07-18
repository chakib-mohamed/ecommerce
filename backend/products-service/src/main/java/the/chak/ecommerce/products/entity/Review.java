package the.chak.ecommerce.products.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "product_id", "reviewer" }))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    private UUID uuid;

    /** UUID of the reviewed {@link Product}. */
    @Column(nullable = false)
    private UUID productId;

    /** Identity of the review's author (their account email) — one review per reviewer per product. */
    @Column(nullable = false)
    private String reviewer;

    @Column(nullable = false)
    private Integer stars;

    @Column(length = 2000)
    private String text;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
