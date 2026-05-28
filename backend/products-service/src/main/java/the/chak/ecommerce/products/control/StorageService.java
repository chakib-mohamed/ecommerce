package the.chak.ecommerce.products.control;

import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ApplicationScoped
public class StorageService {

    @Inject
    S3Client s3;

    @ConfigProperty(name = "products.images.bucket")
    String bucketName;

    @PostConstruct
    void init() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    public String uploadImage(byte[] data) {
        String key = UUID.randomUUID().toString();
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentLength((long) data.length)
                        .contentType(detectContentType(data))
                        .build(),
                RequestBody.fromBytes(data));
        return key;
    }

    public byte[] downloadImage(String key) {
        return s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucketName).key(key).build())
                .asByteArray();
    }

    public void deleteImage(String key) {
        s3.deleteObject(
                DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    private String detectContentType(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50
                && (data[2] & 0xFF) == 0x4E && (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F'
                && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B'
                && data[11] == 'P') {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}
