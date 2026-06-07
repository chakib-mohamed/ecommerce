package the.chak.ecommerce.products.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ProductMongoRepository implements PanacheMongoRepository<ProductMongoEntity> {

    public ProductMongoEntity findByUuid(UUID productID) {
        return find("productID", productID).firstResult();
    }

    public void deleteByProductId(UUID productID) {
        delete("productID = :productID", Map.of("productID", productID));
    }

    public List<ProductMongoEntity> list(int pageIndex, int pageSize) {
        return findAll().page(pageIndex, pageSize).list();
    }
}
