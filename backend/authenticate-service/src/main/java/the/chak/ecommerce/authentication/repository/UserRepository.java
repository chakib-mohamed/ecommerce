package the.chak.ecommerce.authentication.repository;

import java.util.Optional;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.authentication.entity.User;

@ApplicationScoped
public class UserRepository implements PanacheMongoRepository<User> {

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public long countByEmail(String email) {
        return find("email", email).count();
    }
}
