package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.ProductMongoEntity;
import the.chak.ecommerce.products.repository.ProductMongoRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductMongoRepository productMongoRepository;

    @InjectMocks
    ProductService productService;

    private ArgumentCaptor<ProductMongoEntity> entityCaptor;

    @BeforeEach
    void setup() {
        reset(productMongoRepository);
        entityCaptor = ArgumentCaptor.forClass(ProductMongoEntity.class);
    }

    // onProductUpdated tests

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

        when(productMongoRepository.findByUuid(uuid)).thenReturn(null);

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then - verify repository received the persisted entity with mapped fields
        verify(productMongoRepository).persistOrUpdate(entityCaptor.capture());
        ProductMongoEntity entity = entityCaptor.getValue();
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
        // given - first update
        UUID uuid = UUID.randomUUID();
        ProductDto first = new ProductDto();
        first.setUuid(uuid);
        first.setDescription("Original");
        first.setPrice(10.0);
        first.setTitle("Old Title");
        first.setCategories(List.of());
        first.setPromotions(List.of());

        ProductMongoEntity existing = new ProductMongoEntity();
        existing.setProductID(uuid);
        existing.setDescription("Original");
        when(productMongoRepository.findByUuid(uuid)).thenReturn(existing);

        productService.onProductUpdated(new ProductUpdatedEvent(first));

        // second update - same UUID, different data
        ProductDto second = new ProductDto();
        second.setUuid(uuid);
        second.setDescription("Updated");
        second.setPrice(20.0);
        second.setTitle("New Title");
        second.setCategories(List.of());
        second.setPromotions(List.of());

        reset(productMongoRepository);
        when(productMongoRepository.findByUuid(uuid)).thenReturn(existing);

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(second));

        // then - verify the existing entity was updated with new values
        verify(productMongoRepository).persistOrUpdate(entityCaptor.capture());
        ProductMongoEntity updatedEntity = entityCaptor.getValue();
        assertEquals("Updated", updatedEntity.getDescription());
        assertEquals(20.0, updatedEntity.getPrice(), 0.001);
    }

    @Test
    void onProductUpdated_nullCategories_usesEmptyList() {
        // given - ProductDto with null categories
        UUID uuid = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setUuid(uuid);
        dto.setDescription("desc");
        dto.setPrice(5.0);
        dto.setTitle("Item");
        dto.setCategories(null);
        dto.setPromotions(List.of());

        when(productMongoRepository.findByUuid(uuid)).thenReturn(null);

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then - verify categories list is empty, not null
        verify(productMongoRepository).persistOrUpdate(entityCaptor.capture());
        ProductMongoEntity entity = entityCaptor.getValue();
        assertNotNull(entity.getCategories());
        assertTrue(entity.getCategories().isEmpty());
    }

    @Test
    void onProductUpdated_nullPromotions_usesEmptyList() {
        // given - ProductDto with null promotions
        UUID uuid = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setUuid(uuid);
        dto.setDescription("desc");
        dto.setPrice(5.0);
        dto.setTitle("Item");
        dto.setCategories(List.of());
        dto.setPromotions(null);

        when(productMongoRepository.findByUuid(uuid)).thenReturn(null);

        // when
        productService.onProductUpdated(new ProductUpdatedEvent(dto));

        // then - verify promotions list is empty, not null
        verify(productMongoRepository).persistOrUpdate(entityCaptor.capture());
        ProductMongoEntity entity = entityCaptor.getValue();
        assertNotNull(entity.getPromotions());
        assertTrue(entity.getPromotions().isEmpty());
    }

    // onProductDeleted tests

    @Test
    void onProductDeleted_existingProduct_removesFromDatabase() {
        // given
        UUID uuid = UUID.randomUUID();

        // when
        productService.onProductDeleted(new ProductDeletedEvent(uuid));

        // then - verify delete was called with correct UUID
        verify(productMongoRepository).delete(eq("productID = :productID"),
            eq(Map.of("productID", uuid)));
    }

    @Test
    void onProductDeleted_nonExistentProduct_completesWithoutError() {
        // given - UUID not in DB
        UUID uuid = UUID.randomUUID();

        // when / then - must not throw
        productService.onProductDeleted(new ProductDeletedEvent(uuid));

        // then - verify delete was attempted (no exception means success)
        verify(productMongoRepository).delete(eq("productID = :productID"),
            eq(Map.of("productID", uuid)));
    }
}
