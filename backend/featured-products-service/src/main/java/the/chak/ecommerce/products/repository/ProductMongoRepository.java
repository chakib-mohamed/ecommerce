package the.chak.ecommerce.products.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.UUID;

@ApplicationScoped
public class ProductMongoRepository implements PanacheMongoRepository<ProductMongoEntity> {

    public ProductMongoEntity findByUuid(UUID productID) {
        return find("productID", productID).firstResult();
    }
}
