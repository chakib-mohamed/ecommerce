package the.chak.ecommerce.orders.entity;

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
    private Double price;
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
