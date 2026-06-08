package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class SeedImageAssignerTest {

    private static final UUID UUID_ = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final String KEY = "seed-marble-dining-table";

    @InjectMocks
    SeedImageAssigner seedImageAssigner;

    @Mock
    ProductRepository productRepository;

    @Test
    @DisplayName("Sets the image key and returns true when the product has none yet")
    void assignImageKey_productWithoutKey_setsKeyAndReturnsTrue() {
        // given
        Product product = new Product();
        when(productRepository.findByUuid(UUID_)).thenReturn(product);

        // when
        boolean updated = seedImageAssigner.assignImageKey(UUID_, KEY);

        // then
        assertTrue(updated);
        assertEquals(KEY, product.getImageKey());
    }

    @Test
    @DisplayName("Leaves an existing image key untouched and returns false")
    void assignImageKey_productWithExistingKey_skipsAndReturnsFalse() {
        // given
        Product product = new Product();
        product.setImageKey("api-uploaded-key");
        when(productRepository.findByUuid(UUID_)).thenReturn(product);

        // when
        boolean updated = seedImageAssigner.assignImageKey(UUID_, KEY);

        // then
        assertFalse(updated);
        assertEquals("api-uploaded-key", product.getImageKey());
    }

    @Test
    @DisplayName("Returns false when no product exists for the uuid")
    void assignImageKey_missingProduct_returnsFalse() {
        // given
        when(productRepository.findByUuid(UUID_)).thenReturn(null);

        // when
        boolean updated = seedImageAssigner.assignImageKey(UUID_, KEY);

        // then
        assertFalse(updated);
    }
}
