package the.chak.ecommerce.pricing.entity;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

@MongoEntity(collection = "prices")
public class Price {
    public ObjectId id;
    public String productId;
    public Double price;
}
