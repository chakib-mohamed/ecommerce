package the.chak.ecommerce.products.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.Promotion;

@ApplicationScoped
public class PromotionRepository implements PanacheRepository<Promotion> {
}
