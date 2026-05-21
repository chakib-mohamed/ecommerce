package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.control.exceptions.PromotionNotFoundException;
import the.chak.ecommerce.products.entity.Promotion;

@Transactional
@ApplicationScoped
public class PromotionService {

    public Promotion savePromotion(Promotion promotion) {
        promotion.persist();
        return promotion;
    }

    public void deletePromotion(Long promotionID) {
        if (!Promotion.deleteById(promotionID)) {
            throw new PromotionNotFoundException(promotionID);
        }
    }
}
