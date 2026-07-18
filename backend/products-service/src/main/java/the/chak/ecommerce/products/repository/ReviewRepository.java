package the.chak.ecommerce.products.repository;

import java.util.Optional;
import java.util.UUID;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.Review;

@ApplicationScoped
public class ReviewRepository implements PanacheRepository<Review> {

    public Optional<Review> findByProductAndReviewer(UUID productId, String reviewer) {
        return find("productId = ?1 and reviewer = ?2", productId, reviewer).firstResultOptional();
    }

    public Optional<Review> findByUuidOptional(UUID uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public java.util.List<Review> findByProduct(UUID productId, int pageIndex, int pageSize) {
        return find("productId", productId).page(pageIndex, pageSize).list();
    }

    /** Average stars and review count for a product, or {@code null}/{@code 0} when it has none. */
    public Object[] aggregateForProduct(UUID productId) {
        return getEntityManager()
                .createQuery("select avg(r.stars), count(r) from Review r where r.productId = :productId",
                        Object[].class)
                .setParameter("productId", productId)
                .getSingleResult();
    }
}
