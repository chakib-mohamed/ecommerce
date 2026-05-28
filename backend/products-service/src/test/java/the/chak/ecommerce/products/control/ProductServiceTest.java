package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.products.entity.Product;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StorageTestResource.class)
@TestTransaction
class ProductServiceTest {

    @Inject
    ProductService productService;

    @InjectMock
    StorageService storageService;

    // ── saveProduct ────────────────────────────────────────────────────────

    @Test
    void saveProduct_withImageBytes_uploadsImageAndPersists() {
        // given
        byte[] imageBytes = jpegBytes();
        when(storageService.uploadImage(imageBytes)).thenReturn("uploaded-key");
        Product product = newProduct("Widget");

        // when
        Product result = productService.saveProduct(product, imageBytes);

        // then
        assertNotNull(result.getUuid());
        assertEquals("uploaded-key", result.getImageKey());
        verify(storageService).uploadImage(imageBytes);
    }

    @Test
    void saveProduct_withoutImage_persistsDirectly() {
        // given
        Product product = newProduct("Widget");

        // when
        Product result = productService.saveProduct(product, null);

        // then
        assertNotNull(result.getUuid());
        assertNull(result.getImageKey());
        verify(storageService, never()).uploadImage(any());
    }

    // ── updateProduct ──────────────────────────────────────────────────────

    @Test
    void updateProduct_productNotFound_throwsProductNotFoundException() {
        // given — product with UUID that does not exist in DB
        Product product = newProduct("Ghost");
        product.setUuid(UUID.randomUUID());

        // when / then
        assertThrows(ProductNotFoundException.class,
                () -> productService.updateProduct(product, null));
    }

    @Test
    void updateProduct_withNewImage_replacesOldImageAndUpdates() {
        // given — persist a product then mark it as having an old image key
        Product existing = newProduct("Old Widget");
        productService.saveProduct(existing, null);
        existing.setImageKey("old-key"); // dirty in Hibernate session; flushed before next query

        byte[] newBytes = jpegBytes();
        when(storageService.uploadImage(newBytes)).thenReturn("new-key");

        Product update = newProduct("New Widget");
        update.setUuid(existing.getUuid());

        // when
        Product result = productService.updateProduct(update, newBytes);

        // then
        assertEquals("new-key", result.getImageKey());
        verify(storageService).uploadImage(newBytes);
        verify(storageService).deleteImage("old-key");
    }

    @Test
    void updateProduct_withoutImage_retainsExistingImageKey() {
        // given
        Product existing = newProduct("Widget");
        productService.saveProduct(existing, null);
        existing.setImageKey("existing-key");

        Product update = newProduct("Updated Widget");
        update.setUuid(existing.getUuid());

        // when
        Product result = productService.updateProduct(update, null);

        // then
        assertEquals("existing-key", result.getImageKey());
        verify(storageService, never()).uploadImage(any());
        verify(storageService, never()).deleteImage(any());
    }

    // ── deleteProduct ──────────────────────────────────────────────────────

    @Test
    void deleteProduct_withImage_deletesRecordAndStorageKey() {
        // given
        Product product = newProduct("To Delete");
        productService.saveProduct(product, null);
        product.setImageKey("img-key");
        UUID uuid = product.getUuid();

        // when
        productService.deleteProduct(uuid);

        // then
        assertNull(Product.<Product>find("uuid", uuid).firstResult());
        verify(storageService).deleteImage("img-key");
    }

    @Test
    void deleteProduct_withoutImage_deletesRecordOnly() {
        // given
        Product product = newProduct("No Image Product");
        productService.saveProduct(product, null);
        UUID uuid = product.getUuid();

        // when
        productService.deleteProduct(uuid);

        // then
        assertNull(Product.<Product>find("uuid", uuid).firstResult());
        verify(storageService, never()).deleteImage(any());
    }

    // ── updatePrice ────────────────────────────────────────────────────────

    @Test
    void updatePrice_knownProduct_updatesPrice() {
        // given
        Product product = newProduct("Priced Widget");
        productService.saveProduct(product, null);
        String productId = product.getUuid().toString();

        // when
        productService.updatePrice(productId, 25.0);

        // then
        Product updated = Product.<Product>find("uuid", product.getUuid()).firstResult();
        assertEquals(25.0, updated.getPrice(), 0.001);
    }

    @Test
    void updatePrice_unknownProduct_logsAndReturnsWithoutError() {
        // must not throw for an unknown product ID
        productService.updatePrice(UUID.randomUUID().toString(), 50.0);
    }

    // ── getProducts ────────────────────────────────────────────────────────

    @Test
    void getProducts_returnsPaginatedList() {
        // given — persist 3 products
        for (int i = 0; i < 3; i++) {
            productService.saveProduct(newProduct("Widget " + i), null);
        }

        // when
        List<Product> page = productService.getProducts(0, 2);

        // then — page size is honoured
        assertTrue(page.size() <= 2);
    }

    // ── getProductWithAssociations ─────────────────────────────────────────

    @Test
    void getProductWithAssociations_existingProduct_returnsProduct() {
        // given
        Product product = newProduct("Assoc Widget");
        productService.saveProduct(product, null);

        // when
        Optional<Product> result = productService.getProductWithAssociations(product.getUuid());

        // then
        assertTrue(result.isPresent());
        assertEquals(product.getUuid(), result.get().getUuid());
    }

    @Test
    void getProductWithAssociations_nonExistentProduct_returnsEmpty() {
        Optional<Product> result = productService.getProductWithAssociations(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    // ── findByCriteria ─────────────────────────────────────────────────────

    @Test
    void findByCriteria_allowedField_returnsMatchingResults() {
        // given
        String title = "Searchable-" + UUID.randomUUID();
        Product product = newProduct(title);
        productService.saveProduct(product, null);

        // when
        List<Product> results = productService.findByCriteria(
                Map.of("title", new Criteria(Criteria.Operator.EQUALS, title)), 0, 10);

        // then
        assertTrue(results.stream().anyMatch(p -> title.equals(p.getTitle())));
    }

    @Test
    void findByCriteria_invalidField_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> productService.findByCriteria(
                        Map.of("unknown_field", new Criteria(Criteria.Operator.EQUALS, "x")), 0, 10));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Product newProduct(String title) {
        Product p = new Product();
        p.setTitle(title);
        p.setDescription("description");
        p.setPrice(9.99);
        return p;
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
