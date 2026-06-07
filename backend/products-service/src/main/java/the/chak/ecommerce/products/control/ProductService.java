package the.chak.ecommerce.products.control;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.OutboxRepository;
import the.chak.ecommerce.products.repository.ProductRepository;

@ApplicationScoped
public class ProductService {

    private static final Logger LOG = Logger.getLogger(ProductService.class);

    private static final Set<String> ALLOWED_PRODUCT_FIELDS =
            Set.of("uuid", "description", "imageKey", "price", "title");

    @Inject
    ProductRepository productRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    ProductEventMapper productEventMapper;

    @Inject
    Event<OutboxAppended> outboxAppended;

    @Inject
    StorageService storageService;

    @Transactional
    public Optional<Product> getProductWithAssociations(UUID uuid) {
        var maybeProduct = productRepository.findByUuidWithPromotions(uuid);
        if (maybeProduct.isEmpty()) {
            return Optional.empty();
        }
        Product product = maybeProduct.get();
        productRepository.primeCategories(product.id);
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
        LOG.infof("Product created productId=%s title=%s", product.getUuid(), product.getTitle());
        return product;
    }

    @Transactional
    void persistProduct(Product product) {
        productRepository.persist(product);
        appendProductUpdated(product);
    }

    public Product updateProduct(Product product, byte[] imageBytes) {
        var existing = productRepository.findByUuid(product.getUuid());
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
        LOG.infof("Product updated productId=%s title=%s", product.getUuid(), product.getTitle());
        return product;
    }

    @Transactional
    void mergeProduct(Product product) {
        productRepository.merge(product);
        appendProductUpdated(product);
    }

    /**
     * Records a {@code product-updated} outbox row in the current transaction and signals the relay
     * post-commit. The row commits atomically with the business write; the signal is a best-effort
     * nudge so the relay publishes without waiting for the next scheduled tick.
     */
    private void appendProductUpdated(Product product) {
        ProductDto dto = productEventMapper.toDto(product);
        OutboxEvent row = outboxEventFactory.productUpdated(product.getUuid(), new ProductUpdatedEvent(dto));
        outboxRepository.persist(row);
        outboxAppended.fire(OutboxAppended.INSTANCE);
    }

    public void deleteProduct(UUID uuid) {
        String imageKey = deleteProductRecord(uuid);
        if (imageKey != null) {
            storageService.deleteImage(imageKey);
        }
    }

    @Transactional
    String deleteProductRecord(UUID uuid) {
        var product = productRepository.findByUuid(uuid);
        if (product == null) {
            return null;
        }
        String imageKey = product.getImageKey();
        productRepository.delete(product);
        OutboxEvent row = outboxEventFactory.productDeleted(
                product.getUuid(), new ProductDeletedEvent(product.getUuid()));
        outboxRepository.persist(row);
        outboxAppended.fire(OutboxAppended.INSTANCE);
        LOG.infof("Product deleted productId=%s", uuid);
        return imageKey;
    }

    @Transactional
    public void updatePrice(String productId, Double newPrice) {
        Product product = productRepository.findByUuid(UUID.fromString(productId));
        if (product == null) {
            LOG.warnf("Price-changed event for unknown product %s - discarding", productId);
            return;
        }
        product.setPrice(newPrice);
        LOG.infof("Price updated on product productId=%s newPrice=%s", productId, newPrice);
    }

    @Transactional
    public List<Product> getProducts(int pageIndex, int pageSize) {
        List<Product> products = productRepository.listWithPromotions(pageIndex, pageSize);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(p -> p.id).toList();
        productRepository.primeCategories(ids);
        return products;
    }

    public List<Product> findByCriteria(Map<String, Criteria> params, int pageIndex, int pageSize) {
        params.keySet().forEach(key -> {
            if (!ALLOWED_PRODUCT_FIELDS.contains(key)) {
                throw new BadRequestException("Invalid search field: " + key);
            }
        });
        return productRepository.findByCriteria(
                CriteriaMapper.toQueryCriteria(params), pageIndex, pageSize);
    }
}
