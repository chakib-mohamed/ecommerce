package the.chak.ecommerce.products.control;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.products.entity.Product;

@ApplicationScoped
public class ProductService {

    private static final Logger LOG = Logger.getLogger(ProductService.class);

    private static final Set<String> ALLOWED_PRODUCT_FIELDS =
            Set.of("uuid", "description", "imageKey", "price", "title");

    @Inject
    EntityManager em;

    @Inject
    Event<ProductDeletedEvent> productDeletedEvent;

    @Inject
    MinioService minioService;

    @Transactional
    public Product saveProduct(Product product, byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length > 0) {
            String imageKey = minioService.uploadImage(imageBytes);
            product.setImageKey(imageKey);
        }
        product.persist();
        return product;
    }

    @Transactional
    public Product updateProduct(Product product, byte[] imageBytes) {
        var existing = Product.<Product>find("uuid", product.getUuid()).firstResult();
        if (existing == null) {
            throw new ProductNotFoundException(product.getUuid());
        }
        product.id = existing.id;
        if (imageBytes != null && imageBytes.length > 0) {
            if (existing.getImageKey() != null) {
                minioService.deleteImage(existing.getImageKey());
            }
            String imageKey = minioService.uploadImage(imageBytes);
            product.setImageKey(imageKey);
        } else {
            product.setImageKey(existing.getImageKey());
        }
        em.merge(product);
        return product;
    }

    @Transactional
    public void deleteProduct(UUID uuid) {
        var product = Product.<Product>find("uuid", uuid).firstResult();
        if (product != null) {
            if (product.getImageKey() != null) {
                minioService.deleteImage(product.getImageKey());
            }
            product.delete();
            productDeletedEvent.fire(new ProductDeletedEvent(product.getUuid()));
        }
    }

    @Transactional
    public void updatePrice(String productId, Double newPrice) {
        Product product = Product.<Product>find("uuid", UUID.fromString(productId)).firstResult();
        if (product == null) {
            LOG.warnf("Price-changed event for unknown product %s — discarding", productId);
            return;
        }
        product.setPrice(newPrice);
    }

    public List<Product> findByCriteria(Map<String, Criteria> params, int pageIndex, int pageSize) {
        var query = new StringBuilder("1=1");
        params.forEach((key, criteria) -> {
            if (!ALLOWED_PRODUCT_FIELDS.contains(key)) {
                throw new BadRequestException("Invalid search field: " + key);
            }
            query.append(" and ").append(key)
                    .append(criteria.getOperator().getValue()).append(" :").append(key);
        });

        return Product
                .find(query.toString(),
                        params.entrySet().stream().collect(
                                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())))
                .page(pageIndex, pageSize).list();
    }
}
