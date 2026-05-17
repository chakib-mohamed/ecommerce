package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.control.events.PromotionDataChangeEvent;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import chakmed.ecommerce.products.entity.Promotion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@Transactional
@ApplicationScoped
public class PromotionService {

    @Inject
    EntityManager em;

    @Inject
    Event<PromotionDataChangeEvent> promotionDataChangeEvent;

    @Inject
    ProductService productService;

    public Promotion savePromotion(Promotion promotion) {
        promotion.persist();
//        Product product = Product.findById(promotion..id);
//        product.setPromotions(Optional.ofNullable(product.getPromotions()).map(p -> {p.add(promotion); return p;}).orElse(Set.of(promotion
//        )));
//
//        promotionDataChangeEvent.fire(new PromotionDataChangeEvent(product));

       return promotion;
    }

    public void deletePromotion(Long promotionID) {
        Promotion promotion = Promotion.findById(promotionID);
        Promotion.deleteById(promotionID);
        em.flush();

//        Product product = Product.findById(promotion.product.id);
//        promotionDataChangeEvent.fire(new PromotionDataChangeEvent(product));

    }


    void updateProductMongoEntity(@Observes PromotionDataChangeEvent promotionDataChangeEvent) {
        ProductMongoEntity productMongoEntity = productService.mapProductToProductMongoEntity(promotionDataChangeEvent.getProduct());
        productMongoEntity.update();
    }


}
