package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class StorageServiceTest {

    @Inject
    StorageService storageService;

    // ── uploadImage / detectContentType ────────────────────────────────────

    @Test
    void uploadImage_jpegBytes_returnsNonNullKey() {
        // JPEG magic: FF D8 FF
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10};
        String key = storageService.uploadImage(jpeg);
        assertNotNull(key);
    }

    @Test
    void uploadImage_pngBytes_returnsNonNullKey() {
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        String key = storageService.uploadImage(png);
        assertNotNull(key);
    }

    @Test
    void uploadImage_gifBytes_returnsNonNullKey() {
        // GIF magic: 47 49 46 (GIF) + 3 padding bytes to meet ≥6 length check
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a'};
        String key = storageService.uploadImage(gif);
        assertNotNull(key);
    }

    @Test
    void uploadImage_webpBytes_returnsNonNullKey() {
        // WebP: RIFF....WEBP (12 bytes minimum)
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        String key = storageService.uploadImage(webp);
        assertNotNull(key);
    }

    @Test
    void uploadImage_unknownBytes_returnsNonNullKey() {
        byte[] unknown = {0x00, 0x01, 0x02, 0x03};
        String key = storageService.uploadImage(unknown);
        assertNotNull(key);
    }

    // ── downloadImage ──────────────────────────────────────────────────────

    @Test
    void downloadImage_existingKey_returnsSameBytes() {
        // given
        byte[] original = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11, 0x22};
        String key = storageService.uploadImage(original);

        // when
        byte[] downloaded = storageService.downloadImage(key);

        // then
        assertArrayEquals(original, downloaded);
    }

    // ── deleteImage ────────────────────────────────────────────────────────

    @Test
    void deleteImage_existingKey_removesObject() {
        // given
        byte[] data = {'G', 'I', 'F', '8', '7', 'a'};
        String key = storageService.uploadImage(data);

        // when
        storageService.deleteImage(key);

        // then — downloading the deleted key must throw
        assertThrows(NoSuchKeyException.class, () -> storageService.downloadImage(key));
    }
}
