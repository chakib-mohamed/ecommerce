package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.control.events.PromotionUpdatedEvent;
import the.chak.ecommerce.products.entity.Promotion;

@Transactional
@ApplicationScoped
public class PromotionService {

    @Inject
    EntityManager em;

    @Inject
    Event<PromotionUpdatedEvent> promotionDataChangeEvent;

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




}
