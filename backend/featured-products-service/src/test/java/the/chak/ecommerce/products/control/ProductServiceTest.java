package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.MongoDbTestResource;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class ProductServiceTest {

    @Inject
    ProductService productService;

    @BeforeEach
    void cleanup() {
        ProductMongoEntity.deleteAll();
    }

    // ── onProductUpdated ───────────────────────────────────────────────────

    @Test
    void onProductUpdated_newProduct_persistsWithAllMappedFields() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setUuid(uuid);
        dto.setDescription("A great product");
        dto.setImageKey("img-key");
        dto.setPrice(49.99);
        dto.setTitle("Widget");

        CategoryDto cat = new CategoryDto();
        cat.setId(1L);
        cat.setLabel("Electronics");
        dto.setCategories(List.of(cat));

        PromotionDto promo = new PromotionDto();
        promo.setLabel("Summer Sale");
        promo.setPercentageOff(10.0);
        promo.setActiveFrom(LocalDate.of(2025, 6, 1));
        promo.setActiveTo(LocalDate.of(2025, 8, 31));
        dto.setPromotions(List.of(promo));

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertNotNull(entity);
        assertEquals(uuid, entity.getProductID());
        assertEquals("A great product", entity.getDescription());
        assertEquals("img-key", entity.getImage());
        assertEquals(49.99, entity.getPrice(), 0.001);
        assertEquals(1, entity.getCategories().size());
        assertEquals("Electronics", entity.getCategories().get(0).getLabel());
        assertEquals(1, entity.getPromotions().size());
        assertEquals("Summer Sale", entity.getPromotions().get(0).getLabel());
        assertEquals(10.0, entity.getPromotions().get(0).getPercentageOff(), 0.001);
    }

    @Test
    void onProductUpdated_existingProduct_updatesInPlace() {
        // given — first upsert
        UUID uuid = UUID.randomUUID();
        ProductDto first = new ProductDto();
        first.setUuid(uuid);
        first.setDescription("Original");
        first.setPrice(10.0);
        first.setTitle("Old Title");
        first.setCategories(List.of());
        first.setPromotions(List.of());
        productService.onProductUpdated(new ProductUpdatedEvent(first));

        // second upsert — same UUID, different data
        ProductDto second = new ProductDto();
        second.setUuid(uuid);
        second.setDescription("Updated");
        second.setPrice(20.0);
        second.setTitle("New Title");
        second.setCategories(List.of());
        second.setPromotions(List.of());

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(second));

        // then — only one document exists with the updated values
        assertEquals(1L, ProductMongoEntity.count());
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertEquals("Updated", entity.getDescription());
        assertEquals(20.0, entity.getPrice(), 0.001);
    }

    @Test
    void onProductUpdated_nullCategories_usesEmptyList() {
        // given — ProductDto with null categories
        UUID uuid = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setUuid(uuid);
        dto.setDescription("desc");
        dto.setPrice(5.0);
        dto.setTitle("Item");
        dto.setCategories(null);
        dto.setPromotions(List.of());

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then — categories list is empty, not null
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertNotNull(entity.getCategories());
        assertTrue(entity.getCategories().isEmpty());
    }

    @Test
    void onProductUpdated_nullPromotions_usesEmptyList() {
        // given — ProductDto with null promotions
        UUID uuid = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setUuid(uuid);
        dto.setDescription("desc");
        dto.setPrice(5.0);
        dto.setTitle("Item");
        dto.setCategories(List.of());
        dto.setPromotions(null);

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then — promotions list is empty, not null
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertNotNull(entity.getPromotions());
        assertTrue(entity.getPromotions().isEmpty());
    }

    // ── onProductDeleted ───────────────────────────────────────────────────

    @Test
    void onProductDeleted_existingProduct_removesFromDatabase() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductMongoEntity entity = new ProductMongoEntity();
        entity.setProductID(uuid);
        entity.setDescription("Delete Me");
        entity.persist();

        // when
        productService.onProductDeleted(new ProductDeletedEvent(uuid));

        // then
        assertNull(ProductMongoEntity.findByUuid(uuid));
    }

    @Test
    void onProductDeleted_nonExistentProduct_completesWithoutError() {
        // given — UUID not in DB
        UUID uuid = UUID.randomUUID();

        // when / then — must not throw
        productService.onProductDeleted(new ProductDeletedEvent(uuid));
        assertEquals(0L, ProductMongoEntity.count());
    }
}
