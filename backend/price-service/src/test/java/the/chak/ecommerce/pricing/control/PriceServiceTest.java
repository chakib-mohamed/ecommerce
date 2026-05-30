package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.mongodb.panache.PanacheQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;
import the.chak.ecommerce.pricing.entity.Price;
import the.chak.ecommerce.pricing.repository.PriceRepository;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @InjectMocks
    PriceService priceService;

    @Mock
    KafkaPriceEventPublisher publisher;

    @Mock
    PriceRepository priceRepository;

    // -- update validation --------

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a null price")
    void update_nullPrice_throwsInvalidPriceException() {
        // given

        // when & then
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", null));
    }

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a zero price")
    void update_zeroPrice_throwsInvalidPriceException() {
        // given

        // when & then
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", 0.0));
    }

    @Test
    @DisplayName("Throws InvalidPriceException when updating with a negative price")
    void update_negativePrice_throwsInvalidPriceException() {
        // given

        // when & then
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", -5.0));
    }

    // -- update persistence --------

    @Test
    @DisplayName("Creates a new price record and publishes a price-changed event for an unknown product")
    void update_newProductId_createsEntityAndPublishesEvent() {
        // given
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(null);
        when(priceRepository.find("productId", "prod-new")).thenReturn(query);

        // when
        Price result = priceService.update("prod-new", 25.0);

        // then
        assertNotNull(result);
        assertEquals("prod-new", result.productId);
        assertEquals(25.0, result.price, 0.001);
        verify(priceRepository).persistOrUpdate(any(Price.class));
        verify(publisher).publish(any(PriceChangedEvent.class));
    }

    @Test
    @DisplayName("Updates the existing price record and publishes a price-changed event for a known product")
    void update_existingProductId_updatesEntityAndPublishesEvent() {
        // given
        Price existingPrice = new Price();
        existingPrice.productId = "prod-exist";
        existingPrice.price = 10.0;

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(existingPrice);
        when(priceRepository.find("productId", "prod-exist")).thenReturn(query);

        // when
        Price result = priceService.update("prod-exist", 20.0);

        // then
        assertNotNull(result);
        assertEquals("prod-exist", result.productId);
        assertEquals(20.0, result.price, 0.001);
        verify(priceRepository).persistOrUpdate(any(Price.class));
        verify(publisher).publish(any(PriceChangedEvent.class));
    }
}
