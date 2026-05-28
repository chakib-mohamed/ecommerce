package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.control.exceptions.PromotionNotFoundException;
import the.chak.ecommerce.products.entity.Promotion;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StorageTestResource.class)
@TestTransaction
class PromotionServiceTest {

    @Inject
    PromotionService promotionService;

    @Test
    void savePromotion_persistsAndReturnsPromotion() {
        // given
        Promotion promotion = new Promotion();
        promotion.setLabel("Summer Sale");
        promotion.setPercentageOff(20.0);

        // when
        Promotion result = promotionService.savePromotion(promotion);

        // then
        assertNotNull(result.id);
        assertNotNull(Promotion.findById(result.id));
    }

    @Test
    void deletePromotion_existingId_deletesSuccessfully() {
        // given
        Promotion promotion = new Promotion();
        promotion.setLabel("To Delete");
        promotion.setPercentageOff(10.0);
        promotionService.savePromotion(promotion);
        Long id = promotion.id;

        // when
        promotionService.deletePromotion(id);

        // then
        assertNull(Promotion.findById(id));
    }

    @Test
    void deletePromotion_nonExistentId_throwsPromotionNotFoundException() {
        assertThrows(PromotionNotFoundException.class,
                () -> promotionService.deletePromotion(Long.MAX_VALUE));
    }
}
