package the.chak.ecommerce.products.entity;

import java.util.List;
import java.util.UUID;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

@Getter
@Setter
@MongoEntity(collection = "product")
public class ProductMongoEntity {
    public ObjectId id;
    private UUID productID;
    private String description;
    private String image;
    private Double price;
    private String title;

    private List<EmbeddedPromotion> promotions;
    private List<EmbeddedCategory> categories;
}
