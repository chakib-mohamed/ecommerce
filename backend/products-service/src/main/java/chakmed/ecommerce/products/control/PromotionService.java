package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.control.events.PromotionDataChangeEvent;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import chakmed.ecommerce.products.entity.Promotion;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Optional;
import java.util.Set;

@Transactional
@ApplicationScoped
public class PromotionService {

    @Inject
    Event<PromotionDataChangeEvent> promotionDataChangeEvent;

    @Inject
    ProductService productService;

    public Promotion savePromotion(Promotion promotion) {
        promotion.persist();
        Product product = Product.findById(promotion.product.id);
        product.setPromotions(Optional.ofNullable(product.getPromotions()).map(p -> {p.add(promotion); return p;}).orElse(Set.of(promotion
        )));

        promotionDataChangeEvent.fire(new PromotionDataChangeEvent(product));

       return promotion;
    }

    @Inject
    EntityManager em;

    public void deletePromotion(Long promotionID) {
        Promotion promotion = Promotion.findById(promotionID);
        Promotion.deleteById(promotionID);
        em.flush();

        Product product = Product.findById(promotion.product.id);
        promotionDataChangeEvent.fire(new PromotionDataChangeEvent(product));

    }


    void updateProductMongoEntity(@Observes PromotionDataChangeEvent promotionDataChangeEvent) {
        ProductMongoEntity productMongoEntity = productService.mapProductToProductMongoEntity(promotionDataChangeEvent.getProduct());
        productMongoEntity.update();
    }


}
