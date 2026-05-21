package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.EmbeddedCategory;
import the.chak.ecommerce.products.entity.EmbeddedPromotion;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ProductService {

    private static final Logger LOG = Logger.getLogger(ProductService.class);

    public void onProductUpdated(ProductUpdatedEvent productUpdatedEvent) {
        LOG.debug("onProductUpdated received: " + productUpdatedEvent);
        ProductMongoEntity productMongoEntity =
                mapProductToProductMongoEntity(productUpdatedEvent.getProduct());
        productMongoEntity.persistOrUpdate();
    }

    public void onProductDeleted(ProductDeletedEvent productDeletedEvent) {
        LOG.debug("onProductDeleted received: " + productDeletedEvent);
        ProductMongoEntity.delete("productID= :productID",
                Map.of("productID", productDeletedEvent.getProductUuid()));
    }

    private ProductMongoEntity mapProductToProductMongoEntity(ProductDto product) {
        ProductMongoEntity productMongoEntity = ProductMongoEntity.findByUuid(product.getUuid());
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
