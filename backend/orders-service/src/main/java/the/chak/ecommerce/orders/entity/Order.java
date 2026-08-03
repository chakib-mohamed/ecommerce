package the.chak.ecommerce.orders.entity;

import the.chak.ecommerce.orders.boundary.dto.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

@Getter
@Setter
@MongoEntity(collection = "order")
public class Order {
    public ObjectId id;
    private LocalDateTime creationDate;
    private BigDecimal price;

    /** Denomination of {@code price}. One currency, no conversion. */
    private String currency = Money.DEFAULT_CURRENCY;
    private List<ProductVO> products;
    private OrderStatus status;
    private String userID;
    private String processID;

    /**
     * Identifies the saga step currently outstanding. A reply carries it back, so a reply arriving
     * for a step the order has already moved past can be told apart from the current one and
     * discarded. Null when no step is in flight.
     */
    private String sagaStepId;

    /**
     * When the outstanding step stops being worth waiting for. Reservations have no expiry of their
     * own, so this deadline is the only thing that ever frees stock held by a stalled saga.
     */
    private java.time.Instant stepDeadline;

    /**
     * Optimistic-locking counter. Bumped on each guarded write and used as a condition on that
     * write, so a second concurrent update matches nothing rather than silently overwriting.
     * Null on orders written before this field existed.
     */
    private Long version;
}
