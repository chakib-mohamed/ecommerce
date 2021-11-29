package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.control.events.ProductDataChangeEvent;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import org.bson.Document;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
public class ProductService {

    @Inject
    EntityManager em;

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

    public ProductMongoEntity mapProductToProductMongoEntity(Product product) {
        ProductMongoEntity productMongoEntity = ProductMongoEntity.find("productID= :productID", Map.of("productID", product.id))
                                                                    .firstResultOptional()
                                                                    .map(ProductMongoEntity.class::cast)
                                                                    .orElse(new ProductMongoEntity());

        productMongoEntity.setProductID(product.id);
        productMongoEntity.setCategory(product.getCategory().getLabel());
        productMongoEntity.setDescription(product.getDescription());
        productMongoEntity.setImage(product.getImage());
        productMongoEntity.setPrice(product.getPrice());
        productMongoEntity.setPromotions(Optional.ofNullable(product.getPromotions())
                .map(promotions ->
                    promotions.stream().map(promotion -> {
                        Document promotionVO = new Document();
                        promotionVO.put("label", promotion.label);
                        promotionVO.put("activeFrom", promotion.activeFrom);
                        promotionVO.put("activeTo", promotion.activeTo);
                        promotionVO.put("percentageOff", promotion.percentageOff);

                        return promotionVO;
                    }).collect(Collectors.toList())
                ).orElse(null));

        return productMongoEntity;
    }

    public void deleteProduct(Long productID) {
        Product.deleteById(productID);
        ProductMongoEntity.delete("productID= :productID", Map.of("productID", productID));
    }

    void updateProductMongoEntity(@Observes ProductDataChangeEvent productDataChangeEvent) {
        ProductMongoEntity productMongoEntity = this.mapProductToProductMongoEntity(productDataChangeEvent.getProduct());
        productMongoEntity.persistOrUpdate();
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
