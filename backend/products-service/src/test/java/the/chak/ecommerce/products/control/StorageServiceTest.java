package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @InjectMocks
    StorageService storageService;

    @Mock
    S3Client s3;

    @BeforeEach
    void setUp() {
        storageService.bucketName = "test-bucket";
    }

    // -- uploadImage / detectContentType ------------------------------------

    @Test
    @DisplayName("Stores JPEG bytes in S3 and returns a generated key")
    void uploadImage_jpegBytes_returnsGeneratedKey() {
        // given
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10};

        // when
        String key = storageService.uploadImage(jpeg);

        // then
        assertNotNull(key);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Stores PNG bytes in S3 and returns a generated key")
    void uploadImage_pngBytes_returnsGeneratedKey() {
        // given
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

        // when
        String key = storageService.uploadImage(png);

        // then
        assertNotNull(key);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Stores GIF bytes in S3 and returns a generated key")
    void uploadImage_gifBytes_returnsGeneratedKey() {
        // given
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a'};

        // when
        String key = storageService.uploadImage(gif);

        // then
        assertNotNull(key);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Stores WebP bytes in S3 and returns a generated key")
    void uploadImage_webpBytes_returnsGeneratedKey() {
        // given
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        // when
        String key = storageService.uploadImage(webp);

        // then
        assertNotNull(key);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Stores bytes of an unrecognized type in S3 and returns a generated key")
    void uploadImage_unknownBytes_returnsGeneratedKey() {
        // given
        byte[] unknown = {0x00, 0x01, 0x02, 0x03};

        // when
        String key = storageService.uploadImage(unknown);

        // then
        assertNotNull(key);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // -- downloadImage ------------------------------------------------------

    @Test
    @DisplayName("Returns the stored bytes when downloading an existing key")
    void downloadImage_existingKey_returnsBytes() {
        // given
        String key = "test-key";
        byte[] expectedData = {1, 2, 3};
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(expectedData);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        // when
        byte[] downloaded = storageService.downloadImage(key);

        // then
        assertArrayEquals(expectedData, downloaded);
        verify(s3).getObjectAsBytes(any(GetObjectRequest.class));
    }

    // -- deleteImage --------------------------------------------------------

    @Test
    @DisplayName("Issues an S3 delete for the given key")
    void deleteImage_existingKey_callsS3Delete() {
        // given
        String key = "test-key";

        // when
        storageService.deleteImage(key);

        // then
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }
}
