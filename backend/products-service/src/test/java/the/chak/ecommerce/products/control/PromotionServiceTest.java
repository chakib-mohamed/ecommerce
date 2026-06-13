package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.control.exceptions.PromotionNotFoundException;
import the.chak.ecommerce.products.entity.Promotion;
import the.chak.ecommerce.products.repository.PromotionRepository;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @InjectMocks
    PromotionService promotionService;

    @Mock
    PromotionRepository promotionRepository;

    // A real registry so promotion mutation counters are recorded and assertable.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Persists and returns the promotion when it is valid")
    void savePromotion_validPromotion_persistsAndReturnsPromotion() {
        // given
        Promotion promotion = new Promotion();
        promotion.setLabel("Summer Sale");
        promotion.setPercentageOff(20.0);

        // when
        Promotion result = promotionService.savePromotion(promotion);

        // then
        verify(promotionRepository).persist(promotion);
    }

    @Test
    @DisplayName("Deletes the promotion when the id exists")
    void deletePromotion_existingId_deletesSuccessfully() {
        // given
        Long id = 1L;
        when(promotionRepository.deleteById(id)).thenReturn(true);

        // when
        promotionService.deletePromotion(id);

        // then
        verify(promotionRepository).deleteById(id);
    }

    @Test
    @DisplayName("Throws PromotionNotFoundException when deleting an id that does not exist")
    void deletePromotion_nonExistentId_throwsPromotionNotFoundException() {
        // given
        Long id = 999L;
        when(promotionRepository.deleteById(id)).thenReturn(false);

        // when & then
        assertThrows(PromotionNotFoundException.class,
                () -> promotionService.deletePromotion(id));
    }

    @Test
    @DisplayName("Returns all stored promotions")
    void listAll_returnsAllPromotions() {
        // given
        Promotion p1 = new Promotion();
        p1.id = 1L;
        Promotion p2 = new Promotion();
        p2.id = 2L;
        when(promotionRepository.listAll()).thenReturn(List.of(p1, p2));

        // when
        List<Promotion> promotions = promotionService.listAll();

        // then
        assertEquals(2, promotions.size());
        verify(promotionRepository).listAll();
    }

    // --metrics ------------------------------------------------------------

    @Test
    @DisplayName("Counts a create mutation when a valid promotion is saved")
    void savePromotion_validPromotion_recordsCreateMutation() {
        // given
        Promotion promotion = new Promotion();
        promotion.setLabel("Summer Sale");
        promotion.setPercentageOff(20.0);

        // when
        promotionService.savePromotion(promotion);

        // then
        assertEquals(1.0,
                meterRegistry.get("catalog.promotions.mutations").tag("op", "create").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Counts a delete mutation when an existing promotion is removed")
    void deletePromotion_existingId_recordsDeleteMutation() {
        // given
        Long id = 1L;
        when(promotionRepository.deleteById(id)).thenReturn(true);

        // when
        promotionService.deletePromotion(id);

        // then
        assertEquals(1.0,
                meterRegistry.get("catalog.promotions.mutations").tag("op", "delete").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Records no delete mutation when the promotion id does not exist")
    void deletePromotion_nonExistentId_recordsNoDeleteMutation() {
        // given
        Long id = 999L;
        when(promotionRepository.deleteById(id)).thenReturn(false);

        // when & then
        assertThrows(PromotionNotFoundException.class,
                () -> promotionService.deletePromotion(id));
        assertNull(meterRegistry.find("catalog.promotions.mutations").counter());
    }
}
