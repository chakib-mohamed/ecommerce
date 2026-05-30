package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.repository.ProductRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.enterprise.event.Event;
import io.quarkus.hibernate.orm.panache.PanacheQuery;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    ProductService productService;

    @Mock
    ProductRepository productRepository;

    @Mock
    StorageService storageService;

    @Mock
    EntityManager em;

    @Mock
    Event<ProductDeletedEvent> productDeletedEvent;

    @Test
    @DisplayName("Uploads the image and persists the product when image bytes are provided")
    void saveProduct_withImageBytes_uploadsImageAndPersists() {
        // given
        byte[] imageBytes = jpegBytes();
        when(storageService.uploadImage(imageBytes)).thenReturn("uploaded-key");
        Product product = new Product();

        // when
        Product result = productService.saveProduct(product, imageBytes);

        // then
        assertNotNull(result);
        verify(storageService).uploadImage(imageBytes);
        verify(productRepository).persist(product);
    }

    @Test
    @DisplayName("Deletes the uploaded image and rethrows when persistence fails after upload")
    void saveProduct_uploadFails_deletesImageAndThrowsException() {
        // given
        byte[] imageBytes = jpegBytes();
        when(storageService.uploadImage(imageBytes)).thenReturn("uploaded-key");
        Product product = new Product();
        doThrow(new RuntimeException("DB Error")).when(productRepository).persist(product);

        // when & then
        assertThrows(RuntimeException.class, () -> productService.saveProduct(product, imageBytes));
        verify(storageService).deleteImage("uploaded-key");
    }

    @Test
    @DisplayName("Persists the product directly without uploading when no image is provided")
    void saveProduct_withoutImage_persistsDirectly() {
        // given
        Product product = new Product();

        // when
        Product result = productService.saveProduct(product, null);

        // then
        assertNotNull(result);
        verify(storageService, never()).uploadImage(any());
        verify(productRepository).persist(product);
    }

    @Test
    @DisplayName("Throws ProductNotFoundException when updating a product that does not exist")
    void updateProduct_productNotFound_throwsProductNotFoundException() {
        // given
        Product product = new Product();
        UUID uuid = UUID.randomUUID();
        product.setUuid(uuid);

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(null);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        // when & then
        assertThrows(ProductNotFoundException.class,
                () -> productService.updateProduct(product, null));
    }

    @Test
    @DisplayName("Replaces the old image and updates the product when a new image is provided")
    void updateProduct_withNewImage_replacesOldImageAndUpdates() {
        // given
        UUID uuid = UUID.randomUUID();
        Product existing = new Product();
        existing.id = 1L;
        existing.setUuid(uuid);
        existing.setImageKey("old-key");

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(existing);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        byte[] newBytes = jpegBytes();
        when(storageService.uploadImage(newBytes)).thenReturn("new-key");

        Product update = new Product();
        update.setUuid(uuid);

        // when
        Product result = productService.updateProduct(update, newBytes);

        // then
        assertEquals("new-key", result.getImageKey());
        assertEquals(1L, result.id);
        verify(storageService).uploadImage(newBytes);
        verify(storageService).deleteImage("old-key");
        verify(em).merge(update);
    }

    @Test
    @DisplayName("Keeps the existing image key when updating without a new image")
    void updateProduct_withoutImage_retainsExistingImageKey() {
        // given
        UUID uuid = UUID.randomUUID();
        Product existing = new Product();
        existing.id = 1L;
        existing.setUuid(uuid);
        existing.setImageKey("existing-key");

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(existing);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        Product update = new Product();
        update.setUuid(uuid);

        // when
        Product result = productService.updateProduct(update, null);

        // then
        assertEquals("existing-key", result.getImageKey());
        verify(storageService, never()).uploadImage(any());
        verify(storageService, never()).deleteImage(any());
        verify(em).merge(update);
    }

    @Test
    @DisplayName("Deletes the record and its stored image and fires a deleted event for an existing product")
    void deleteProduct_existingProduct_deletesRecordAndStorageKey() {
        // given
        UUID uuid = UUID.randomUUID();
        Product product = new Product();
        product.setImageKey("img-key");
        product.setUuid(uuid);

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(product);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        // when
        productService.deleteProduct(uuid);

        // then
        verify(productRepository).delete(product);
        verify(storageService).deleteImage("img-key");
        verify(productDeletedEvent).fire(any(ProductDeletedEvent.class));
    }

    @Test
    @DisplayName("Does nothing when deleting a product that does not exist")
    void deleteProduct_nonExistentProduct_doesNothing() {
        // given
        UUID uuid = UUID.randomUUID();
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(null);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        // when
        productService.deleteProduct(uuid);

        // then
        verify(storageService, never()).deleteImage(any());
    }

    @Test
    @DisplayName("Updates the stored price for a known product")
    void updatePrice_knownProduct_updatesPrice() {
        // given
        UUID uuid = UUID.randomUUID();
        Product product = new Product();
        product.setUuid(uuid);

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(product);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        // when
        productService.updatePrice(uuid.toString(), 25.0);

        // then
        assertEquals(25.0, product.getPrice(), 0.001);
    }

    @Test
    @DisplayName("Returns quietly without error when updating the price of an unknown product")
    void updatePrice_unknownProduct_logsAndReturns() {
        // given
        UUID uuid = UUID.randomUUID();
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResult()).thenReturn(null);
        when(productRepository.find("uuid", uuid)).thenReturn(query);

        // when
        productService.updatePrice(uuid.toString(), 50.0);

        // then - no exception
    }

    @Test
    @DisplayName("Returns the requested page of products with their associations")
    void getProducts_validPage_returnsPaginatedList() {
        // given
        Product p1 = new Product();
        p1.id = 1L;
        Product p2 = new Product();
        p2.id = 2L;
        List<Product> productsList = List.of(p1, p2);

        PanacheQuery query1 = mock(PanacheQuery.class);
        when(query1.page(0, 2)).thenReturn(query1);
        when(query1.list()).thenReturn(productsList);
        when(productRepository.find("from Product p left join fetch p.promotions")).thenReturn(query1);

        PanacheQuery query2 = mock(PanacheQuery.class);
        when(productRepository.find("from Product p left join fetch p.categories where p.id in ?1", List.of(1L, 2L))).thenReturn(query2);

        // when
        List<Product> page = productService.getProducts(0, 2);

        // then
        assertEquals(2, page.size());
    }

    @Test
    @DisplayName("Returns the product with its promotions and categories when it exists")
    void getProductWithAssociations_existingProduct_returnsProduct() {
        // given
        UUID uuid = UUID.randomUUID();
        Product product = new Product();
        product.id = 1L;
        product.setUuid(uuid);

        PanacheQuery query1 = mock(PanacheQuery.class);
        when(query1.firstResultOptional()).thenReturn(Optional.of(product));
        when(productRepository.find("from Product p left join fetch p.promotions where p.uuid = ?1", uuid)).thenReturn(query1);

        PanacheQuery query2 = mock(PanacheQuery.class);
        when(productRepository.find("from Product p left join fetch p.categories where p.id = ?1", 1L)).thenReturn(query2);

        // when
        Optional<Product> result = productService.getProductWithAssociations(uuid);

        // then
        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().getUuid());
    }

    @Test
    @DisplayName("Returns empty when the product does not exist")
    void getProductWithAssociations_nonExistentProduct_returnsEmpty() {
        // given
        UUID uuid = UUID.randomUUID();
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.firstResultOptional()).thenReturn(Optional.empty());
        when(productRepository.find(anyString(), (Object[]) any())).thenReturn(query);

        // when
        Optional<Product> result = productService.getProductWithAssociations(uuid);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Returns matching products when filtering on an allowed field")
    void findByCriteria_allowedField_returnsMatchingResults() {
        // given
        String title = "Searchable";
        Map<String, Criteria> params = Map.of("title", new Criteria(Criteria.Operator.EQUALS, title));

        PanacheQuery query = mock(PanacheQuery.class);
        when(query.page(0, 10)).thenReturn(query);
        when(query.list()).thenReturn(List.of(new Product()));
        when(productRepository.find(anyString(), anyMap())).thenReturn(query);

        // when
        List<Product> results = productService.findByCriteria(params, 0, 10);

        // then
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Throws BadRequestException when filtering on a field that is not allowed")
    void findByCriteria_invalidField_throwsBadRequestException() {
        // given
        Map<String, Criteria> params = Map.of("unknown_field", new Criteria(Criteria.Operator.EQUALS, "x"));

        // when & then
        assertThrows(BadRequestException.class,
                () -> productService.findByCriteria(params, 0, 10));
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
