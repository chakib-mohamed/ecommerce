package chakmed.ecommerce.products.entity;

import io.quarkus.mongodb.panache.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import lombok.Data;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

@Data
@MongoEntity(collection = "product")
public class ProductMongoEntity extends PanacheMongoEntity {
    private ObjectId id;
    private Long productID;
    private String description;
    private String image;
    private Double price;
    private String title;

    private List<Document> promotions;
    private String category;


}
