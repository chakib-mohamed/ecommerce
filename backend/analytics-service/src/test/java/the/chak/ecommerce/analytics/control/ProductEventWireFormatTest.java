package the.chak.ecommerce.analytics.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.analytics.entity.DimProduct;
import the.chak.ecommerce.analytics.repository.DimProductRepository;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;

/**
 * Ingests a product event exactly as it appears on the wire.
 *
 * <p>The other tests build their payloads with setters, which lets an assumption about the event's
 * shape go unchallenged -- reading a field the producer never sends looks fine until the real
 * stream arrives. This payload is a capture from a running stack, so it fails if what the producer
 * sends and what ingestion reads ever drift apart again.
 */
@ExtendWith(MockitoExtension.class)
class ProductEventWireFormatTest {

    /** Captured from the product-updated topic; the image field is dropped for brevity only. */
    private static final String PRODUCT_UPDATED_PAYLOAD = """
            {"product":{
               "uuid":"a0000000-0000-0000-0000-000000000001",
               "title":"Marble Dining Table",
               "description":"A solid marble dining table",
               "price":899.99,
               "stock":8,
               "categories":[{"id":20,"label":"Dining Tables","sub_categories":[]}],
               "promotions":[]
            }}
            """;

    @InjectMocks
    IngestionService ingestionService;

    @Mock
    FactSalesLineRepository factRepository;

    @Mock
    DimProductRepository dimRepository;

    private static ProductUpdatedEvent decode(String payload) {
        // The same JSON-B settings the service applies, so this reads the wire the way it arrives.
        JsonbConfig config = new JsonbConfig()
                .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES)
                .withNullValues(false);
        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            return jsonb.fromJson(payload, ProductUpdatedEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("could not decode the product event payload", e);
        }
    }

    @Test
    @DisplayName("Takes the category from a product event in the exact shape the producer sends")
    void upsertProduct_realEventPayload_recordsTheCategoryItCarries() {
        // given
        ProductUpdatedEvent event = decode(PRODUCT_UPDATED_PAYLOAD);
        String productId = "a0000000-0000-0000-0000-000000000001";
        when(dimRepository.findByIdOptional(productId)).thenReturn(Optional.empty());

        // the fields ingestion must not depend on: the producer does not send them
        assertNull(event.getProduct().getCategoryId(),
                "a product event carries no category_id; ingestion must not rely on one");
        assertNull(event.getProduct().getSubcategoryId(),
                "a product event carries no subcategory_id; ingestion must not rely on one");

        // when
        ingestionService.upsertProduct(event.getProduct());

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertEquals(productId, captor.getValue().getProductId());
        assertEquals("Marble Dining Table", captor.getValue().getTitle());
        assertEquals(20L, captor.getValue().getCategoryId());
        assertEquals("Dining Tables", captor.getValue().getCategoryLabel());
    }
}
