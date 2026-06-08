package the.chak.ecommerce.products.control;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.ProductRepository;

/**
 * Transactional collaborator for {@link SeedImageInitializer}: assigns a default
 * {@code image_key} to a seeded product. Kept in its own bean so the {@code @Transactional}
 * interceptor actually applies (CDI does not intercept self-invocation) and so the S3 upload
 * stays outside the transaction.
 */
@ApplicationScoped
public class SeedImageAssigner {

    @Inject
    ProductRepository productRepository;

    /**
     * Sets {@code image_key} on the product with the given uuid, but only when it is still
     * null so an API-uploaded image is never overwritten.
     *
     * @return {@code true} if a row was updated, {@code false} if the product is missing or
     *         already has an image key
     */
    @Transactional
    public boolean assignImageKey(UUID uuid, String key) {
        Product product = productRepository.findByUuid(uuid);
        if (product == null || product.getImageKey() != null) {
            return false;
        }
        product.setImageKey(key);
        return true;
    }
}
