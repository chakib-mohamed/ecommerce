package the.chak.ecommerce.orders.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = true)
@MongoEntity(collection = "carts")
public class Cart extends PanacheMongoEntity {
    public String userId;
    public List<CartItem> items = new ArrayList<>();
    public Instant updatedAt;

    public static Optional<Cart> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }
}
