package the.chak.ecommerce.products.entity;

import java.util.List;
import java.util.UUID;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;

@Data
@lombok.EqualsAndHashCode(callSuper = true)
@MongoEntity(collection = "product")
public class ProductMongoEntity extends PanacheMongoEntity {
    private UUID productID;
    private String description;
    private String image;
    private Double price;
    private String title;

    private List<EmbeddedPromotion> promotions;
    private List<EmbeddedCategory> categories;

    public static ProductMongoEntity findByUuid(UUID productID) {
        return find("productID", productID).firstResult();
    }
}
