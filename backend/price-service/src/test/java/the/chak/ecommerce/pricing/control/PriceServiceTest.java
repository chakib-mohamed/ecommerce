package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;
import the.chak.ecommerce.pricing.entity.Price;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class PriceServiceTest {

    @Inject
    PriceService priceService;

    @InjectMock
    KafkaPriceEventPublisher publisher;

    @BeforeEach
    void cleanup() {
        Price.deleteAll();
    }

    // ── update validation ──────────────────────────────────────────────────

    @Test
    void update_nullPrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", null));
    }

    @Test
    void update_zeroPrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", 0.0));
    }

    @Test
    void update_negativePrice_throwsInvalidPriceException() {
        assertThrows(InvalidPriceException.class, () -> priceService.update("prod-1", -5.0));
    }

    // ── update persistence ─────────────────────────────────────────────────

    @Test
    void update_newProductId_createsEntityAndPublishesEvent() {
        // given
        // when
        Price result = priceService.update("prod-new", 25.0);

        // then
        assertNotNull(result);
        assertEquals("prod-new", result.productId);
        assertEquals(25.0, result.price, 0.001);
        assertEquals(1L, Price.count());
        verify(publisher).publish(any(PriceChangedEvent.class));
    }

    @Test
    void update_existingProductId_updatesEntityAndPublishesEvent() {
        // given — create initial entry
        priceService.update("prod-exist", 10.0);

        // when — update the same product
        Price result = priceService.update("prod-exist", 20.0);

        // then — still one document, price updated
        assertEquals(20.0, result.price, 0.001);
        assertEquals(1L, Price.count());
        verify(publisher, org.mockito.Mockito.times(2)).publish(any(PriceChangedEvent.class));
    }
}
