package the.chak.ecommerce.pricing.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.pricing.entity.Price;

@ApplicationScoped
public class PriceRepository implements PanacheMongoRepository<Price> {

    public Price findByProductId(String productId) {
        return find("productId", productId).firstResult();
    }
}
