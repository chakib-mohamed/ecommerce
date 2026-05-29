package the.chak.ecommerce.orders.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@MongoEntity(collection = "carts")
public class Cart {
    public ObjectId id;
    public String userId;
    public List<CartItem> items = new ArrayList<>();
    public Instant updatedAt;
}
