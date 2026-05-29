package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.control.exceptions.PromotionNotFoundException;
import the.chak.ecommerce.products.entity.Promotion;
import the.chak.ecommerce.products.repository.PromotionRepository;

import java.util.List;

@Transactional
@ApplicationScoped
public class PromotionService {

    @Inject
    PromotionRepository promotionRepository;

    public Promotion savePromotion(Promotion promotion) {
        promotionRepository.persist(promotion);
        return promotion;
    }

    public void deletePromotion(Long promotionID) {
        if (!promotionRepository.deleteById(promotionID)) {
            throw new PromotionNotFoundException(promotionID);
        }
    }

    public List<Promotion> listAll() {
        return promotionRepository.listAll();
    }
}
