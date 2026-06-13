package the.chak.ecommerce.products.control;

import io.micrometer.core.instrument.MeterRegistry;
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

    @Inject
    MeterRegistry meterRegistry;

    public Promotion savePromotion(Promotion promotion) {
        promotionRepository.persist(promotion);
        recordPromotionMutation(MetricNames.OP_CREATE);
        return promotion;
    }

    public void deletePromotion(Long promotionID) {
        if (!promotionRepository.deleteById(promotionID)) {
            throw new PromotionNotFoundException(promotionID);
        }
        recordPromotionMutation(MetricNames.OP_DELETE);
    }

    private void recordPromotionMutation(String op) {
        meterRegistry.counter(MetricNames.CATALOG_PROMOTIONS_MUTATIONS, MetricNames.TAG_OP, op).increment();
    }

    public List<Promotion> listAll() {
        return promotionRepository.listAll();
    }
}
