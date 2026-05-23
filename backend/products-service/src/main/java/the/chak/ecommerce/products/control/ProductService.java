package the.chak.ecommerce.products.control;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    StorageService storageService;

    @Transactional
    public Optional<Product> getProductWithAssociations(UUID uuid) {
        var maybeProduct = Product.<Product>find(
                        "from Product p left join fetch p.promotions where p.uuid = ?1", uuid)
                .firstResultOptional();
        if (maybeProduct.isEmpty()) return Optional.empty();
        Product product = maybeProduct.get();
        // Second query loads categories via the session cache — avoids MultipleBagFetchException
        Product.find("from Product p left join fetch p.categories where p.id = ?1", product.id)
                .firstResult();
        return Optional.of(product);
    }

    public Product saveProduct(Product product, byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length > 0) {
            String imageKey = storageService.uploadImage(imageBytes);
            product.setImageKey(imageKey);
            try {
                persistProduct(product);
            } catch (Exception e) {
                storageService.deleteImage(imageKey);
                throw e;
            }
        } else {
            persistProduct(product);
        }
        return product;
    }

    @Transactional
    void persistProduct(Product product) {
        product.persist();
    }

    public Product updateProduct(Product product, byte[] imageBytes) {
        var existing = Product.<Product>find("uuid", product.getUuid()).firstResult();
        if (existing == null) {
            throw new ProductNotFoundException(product.getUuid());
        }
        product.id = existing.id;
        String oldImageKey = existing.getImageKey();

        if (imageBytes != null && imageBytes.length > 0) {
            String newImageKey = storageService.uploadImage(imageBytes);
            product.setImageKey(newImageKey);
            try {
                mergeProduct(product);
            } catch (Exception e) {
                storageService.deleteImage(newImageKey);
                throw e;
            }
            if (oldImageKey != null) {
                storageService.deleteImage(oldImageKey);
            }
        } else {
            product.setImageKey(oldImageKey);
            mergeProduct(product);
        }
        return product;
    }

    @Transactional
    void mergeProduct(Product product) {
        em.merge(product);
    }

    public void deleteProduct(UUID uuid) {
        String imageKey = deleteProductRecord(uuid);
        if (imageKey != null) {
            storageService.deleteImage(imageKey);
        }
    }

    @Transactional
    String deleteProductRecord(UUID uuid) {
        var product = Product.<Product>find("uuid", uuid).firstResult();
        if (product == null) return null;
        String imageKey = product.getImageKey();
        product.delete();
        productDeletedEvent.fire(new ProductDeletedEvent(product.getUuid()));
        return imageKey;
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

    @Transactional
    public List<Product> getProducts(int pageIndex, int pageSize) {
        List<Product> products = Product
                .<Product>find("from Product p left join fetch p.promotions")
                .page(pageIndex, pageSize).list();
        if (products.isEmpty()) return List.of();
        // Second query initializes categories via the session cache — avoids MultipleBagFetchException
        List<Long> ids = products.stream().map(p -> p.id).toList();
        Product.find("from Product p left join fetch p.categories where p.id in ?1", ids).list();
        return products;
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
