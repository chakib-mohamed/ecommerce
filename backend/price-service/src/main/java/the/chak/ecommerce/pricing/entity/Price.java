package the.chak.ecommerce.pricing.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "prices")
public class Price extends PanacheMongoEntity {
    public String productId;
    public Double price;
}
