package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.Map;

@ApplicationScoped
public class ProductService {

    public void onProductUpdated(ProductUpdatedEvent productUpdatedEvent) {
        System.out.println("DEBUG: onProductUpdated received: " + productUpdatedEvent);
        ProductMongoEntity productMongoEntity =
                this.mapProductToProductMongoEntity(productUpdatedEvent.getProduct());
        productMongoEntity.persistOrUpdate();
    }

    public ProductMongoEntity mapProductToProductMongoEntity(ProductDto product) {
        ProductMongoEntity productMongoEntity = ProductMongoEntity.findByUuid(product.getUuid());
        if (productMongoEntity == null) {
            productMongoEntity = new ProductMongoEntity();
        }

        productMongoEntity.setProductID(product.getUuid());
        productMongoEntity.setDescription(product.getDescription());
        productMongoEntity.setImage(product.getImageKey());
        productMongoEntity.setPrice(product.getPrice());
        productMongoEntity.setCategories(
                product.getCategories().stream().map(this::categoryDtoToDocument).toList());
        productMongoEntity.setPromotions(product.getPromotions().stream()
                .map(promotion -> promotionDtoToDocument(promotion)).toList());

        return productMongoEntity;
    }

    private Document categoryDtoToDocument(the.chak.ecommerce.products.boundary.dto.CategoryDto category) {
        return new Document(Map.of("id", category.getId(), "label", category.getLabel()));
    }

    private Document promotionDtoToDocument(PromotionDto promotion) {
        return new Document(Map.of("label", promotion.getLabel(), "activeFrom",
                promotion.getActiveFrom(), "activeTo", promotion.getActiveTo(), "percentageOff",
                promotion.getPercentageOff()));
    }

    public void onProductDeleted(ProductDeletedEvent productDeletedEvent) {
        System.out.println("DEBUG: onProductDeleted received: " + productDeletedEvent);
        ProductMongoEntity.delete("productID= :productID",
                Map.of("productID", productDeletedEvent.getProductUuid()));
    }

}
