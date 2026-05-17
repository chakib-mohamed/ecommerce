package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.boundary.mapper.ProductMapper;
import chakmed.ecommerce.products.control.events.ProductDataChangeEvent;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.bson.Document;

import java.util.List;
import java.util.Map;

@Transactional
@ApplicationScoped
public class ProductService {

    @Inject
    EntityManager em;

    @Inject
    ProductMapper productMapper;

    @Inject
    Event<ProductDataChangeEvent> productDataChangeEvent;


    public Product saveProduct(Product product) {

        product.persist();
        productDataChangeEvent.fire(new ProductDataChangeEvent(product));

        return product;
    }

    public Product updateProduct(Product product) {
        em.merge(product);

        productDataChangeEvent.fire(new ProductDataChangeEvent(product));
        return product;
    }

    public void deleteProduct(Long productID) {
        Product.deleteById(productID);
        ProductMongoEntity.delete("productID= :productID", Map.of("productID", productID));
    }

    void updateProductMongoEntity(@Observes ProductDataChangeEvent productDataChangeEvent) {
        ProductMongoEntity productMongoEntity = this.mapProductToProductMongoEntity(productDataChangeEvent.getProduct());
        productMongoEntity.persistOrUpdate();
    }

    public ProductMongoEntity mapProductToProductMongoEntity(Product product) {
        ProductMongoEntity productMongoEntity = ProductMongoEntity.find("productID= :productID", Map.of("productID", product.id))
                .firstResultOptional()
                .map(ProductMongoEntity.class::cast)
                .orElse(new ProductMongoEntity());

        productMongoEntity.setProductID(product.id);
        productMongoEntity.setDescription(product.getDescription());
        productMongoEntity.setImage(product.getImage());
        productMongoEntity.setPrice(product.getPrice());
        productMongoEntity.setCategories(product.getCategories().stream().map(category -> new Document("label", category.getLabel())).toList());
        productMongoEntity.setPromotions(
                product.getPromotions().stream().map(promotion ->
                        new Document(
                                Map.of("label", promotion.getLabel(),
                                        "activeFrom", promotion.getActiveFrom(),
                                        "activeTo", promotion.getActiveTo(),
                                        "percentageOff", promotion.getPercentageOff())
                        )
                ).toList());

        return productMongoEntity;
    }

    public List<Product> findByCriteria(Map<String, Object> params) {
        var query = new StringBuilder("1=1");
        params.forEach(
                (key, value) -> {
                    query.append(" and ").append(key).append("=").append(" :").append(key);
                }
        );

        return Product.list(query.toString(), params);
    }

}
