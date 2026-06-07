package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.EmbeddedCategory;
import the.chak.ecommerce.products.entity.EmbeddedPromotion;
import the.chak.ecommerce.products.entity.ProductMongoEntity;
import the.chak.ecommerce.products.repository.ProductMongoRepository;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductService {

    private static final Logger LOG = Logger.getLogger(ProductService.class);

    @Inject
    ProductMongoRepository productMongoRepository;

    public void onProductUpdated(ProductUpdatedEvent productUpdatedEvent) {
        ProductMongoEntity productMongoEntity =
                mapProductToProductMongoEntity(productUpdatedEvent.getProduct());
        productMongoRepository.persistOrUpdate(productMongoEntity);
        LOG.infof("Featured product upserted productId=%s", productMongoEntity.getProductID());
    }

    public void onProductDeleted(ProductDeletedEvent productDeletedEvent) {
        productMongoRepository.deleteByProductId(productDeletedEvent.getProductUuid());
        LOG.infof("Featured product deleted productId=%s", productDeletedEvent.getProductUuid());
    }

    public List<ProductMongoEntity> listProducts(int pageIndex, int pageSize) {
        return productMongoRepository.list(pageIndex, pageSize);
    }

    private ProductMongoEntity mapProductToProductMongoEntity(ProductDto product) {
        ProductMongoEntity productMongoEntity = productMongoRepository.findByUuid(product.getUuid());
        if (productMongoEntity == null) {
            productMongoEntity = new ProductMongoEntity();
        }

        productMongoEntity.setProductID(product.getUuid());
        productMongoEntity.setDescription(product.getDescription());
        productMongoEntity.setImage(product.getImageKey());
        productMongoEntity.setPrice(product.getPrice());
        productMongoEntity.setCategories(
                Optional.ofNullable(product.getCategories()).orElse(List.of())
                        .stream().map(this::toEmbeddedCategory).toList());
        productMongoEntity.setPromotions(
                Optional.ofNullable(product.getPromotions()).orElse(List.of())
                        .stream().map(this::toEmbeddedPromotion).toList());

        return productMongoEntity;
    }

    private EmbeddedCategory toEmbeddedCategory(CategoryDto category) {
        EmbeddedCategory embedded = new EmbeddedCategory();
        embedded.setId(category.getId());
        embedded.setLabel(category.getLabel());
        return embedded;
    }

    private EmbeddedPromotion toEmbeddedPromotion(PromotionDto promotion) {
        EmbeddedPromotion embedded = new EmbeddedPromotion();
        embedded.setLabel(promotion.getLabel());
        embedded.setActiveFrom(promotion.getActiveFrom());
        embedded.setActiveTo(promotion.getActiveTo());
        embedded.setPercentageOff(promotion.getPercentageOff());
        return embedded;
    }
}
