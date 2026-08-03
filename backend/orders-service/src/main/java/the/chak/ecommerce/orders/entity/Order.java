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
     * Optimistic-locking counter. Bumped on each guarded write and used as a condition on that
     * write, so a second concurrent update matches nothing rather than silently overwriting.
     * Null on orders written before this field existed.
     */
    private Long version;
}
