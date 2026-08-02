package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Test
    @DisplayName("Stores bytes under the supplied key and returns that key")
    void uploadImage_withExplicitKey_usesThatKeyAndDetectsContentType() {
        // given
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10};
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        // when
        String key = storageService.uploadImage("seed-marble-dining-table", jpeg);

        // then
        assertEquals("seed-marble-dining-table", key);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("seed-marble-dining-table", captor.getValue().key());
        assertEquals("image/jpeg", captor.getValue().contentType());
    }

    // -- downloadImage ------------------------------------------------------

    @Test
    @DisplayName("Returns the stored bytes when downloading an existing key")
    void downloadImage_existingKey_returnsBytes() {
        // given
        String key = "test-key";
        byte[] expectedData = {1, 2, 3};
        ResponseBytes<GetObjectResponse> responseBytes =
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expectedData);
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

    // -- detectContentType: signature sniffing -------------------------------
    // The type is read from the leading bytes, not the filename, so each signature has to be
    // rejected at every position it checks. A near-miss that still passed would let a caller
    // label arbitrary bytes as an image.

    static Stream<Arguments> signatures() {
        return Stream.of(
                // recognised
                Arguments.of("JPEG", bytes(3, 0xFF, 0xD8, 0xFF), "image/jpeg"),
                Arguments.of("PNG", bytes(8, 0x89, 0x50, 0x4E, 0x47), "image/png"),
                Arguments.of("GIF", gif('G', 'I', 'F'), "image/gif"),
                Arguments.of("WebP", webp('R', 'I', 'F', 'F', 'W', 'E', 'B', 'P'), "image/webp"),

                // too short for any signature to be read
                Arguments.of("empty", new byte[0], "application/octet-stream"),
                Arguments.of("two bytes", bytes(2, 0xFF, 0xD8), "application/octet-stream"),

                // JPEG near-misses, one byte off at each checked position
                Arguments.of("JPEG byte 0 wrong", bytes(3, 0x00, 0xD8, 0xFF), "application/octet-stream"),
                Arguments.of("JPEG byte 1 wrong", bytes(3, 0xFF, 0x00, 0xFF), "application/octet-stream"),
                Arguments.of("JPEG byte 2 wrong", bytes(3, 0xFF, 0xD8, 0x00), "application/octet-stream"),

                // PNG near-misses
                Arguments.of("PNG byte 0 wrong", bytes(8, 0x00, 0x50, 0x4E, 0x47), "application/octet-stream"),
                Arguments.of("PNG byte 1 wrong", bytes(8, 0x89, 0x00, 0x4E, 0x47), "application/octet-stream"),
                Arguments.of("PNG byte 2 wrong", bytes(8, 0x89, 0x50, 0x00, 0x47), "application/octet-stream"),
                Arguments.of("PNG byte 3 wrong", bytes(8, 0x89, 0x50, 0x4E, 0x00), "application/octet-stream"),
                Arguments.of("PNG signature but too short", bytes(4, 0x89, 0x50, 0x4E, 0x47),
                        "application/octet-stream"),

                // GIF near-misses
                Arguments.of("GIF byte 0 wrong", gif('X', 'I', 'F'), "application/octet-stream"),
                Arguments.of("GIF byte 1 wrong", gif('G', 'X', 'F'), "application/octet-stream"),
                Arguments.of("GIF byte 2 wrong", gif('G', 'I', 'X'), "application/octet-stream"),

                // WebP near-misses, one byte off at each of the eight checked positions
                Arguments.of("WebP byte 0 wrong", webp('X', 'I', 'F', 'F', 'W', 'E', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 1 wrong", webp('R', 'X', 'F', 'F', 'W', 'E', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 2 wrong", webp('R', 'I', 'X', 'F', 'W', 'E', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 3 wrong", webp('R', 'I', 'F', 'X', 'W', 'E', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 8 wrong", webp('R', 'I', 'F', 'F', 'X', 'E', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 9 wrong", webp('R', 'I', 'F', 'F', 'W', 'X', 'B', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 10 wrong", webp('R', 'I', 'F', 'F', 'W', 'E', 'X', 'P'), "application/octet-stream"),
                Arguments.of("WebP byte 11 wrong", webp('R', 'I', 'F', 'F', 'W', 'E', 'B', 'X'), "application/octet-stream"),
                Arguments.of("WebP header but too short", bytes(11, 'R', 'I', 'F', 'F'),
                        "application/octet-stream"));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("signatures")
    @DisplayName("Reads the content type from the leading bytes")
    void detectContentType_readsSignature(String description, byte[] data, String expected) {
        assertEquals(expected, storageService.detectContentType(data), description);
    }

    /** A buffer of {@code length} bytes whose leading bytes are those given. */
    private static byte[] bytes(int length, int... leading) {
        byte[] data = new byte[length];
        for (int i = 0; i < leading.length && i < length; i++) {
            data[i] = (byte) leading[i];
        }
        return data;
    }

    private static byte[] gif(char a, char b, char c) {
        return bytes(6, a, b, c);
    }

    /** RIFF containers carry the format tag at offset 8, after a four-byte length. */
    private static byte[] webp(char r, char i, char f1, char f2, char w, char e, char b, char p) {
        byte[] data = bytes(12, r, i, f1, f2);
        data[8] = (byte) w;
        data[9] = (byte) e;
        data[10] = (byte) b;
        data[11] = (byte) p;
        return data;
    }
}
