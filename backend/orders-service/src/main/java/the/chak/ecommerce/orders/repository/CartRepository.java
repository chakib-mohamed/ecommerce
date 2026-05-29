package the.chak.ecommerce.orders.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.entity.Cart;

import java.util.Optional;

@ApplicationScoped
public class CartRepository implements PanacheMongoRepository<Cart> {
    public Optional<Cart> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }
}
