package the.chak.ecommerce.products.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.Product;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {
}
