package the.chak.ecommerce.products.control;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MinioService {

    @ConfigProperty(name = "quarkus.s3.endpoint-override")
    String endpoint;

    @ConfigProperty(name = "quarkus.s3.aws.region")
    String region;

    @ConfigProperty(name = "quarkus.s3.aws.credentials.static.access-key-id")
    String accessKey;

    @ConfigProperty(name = "quarkus.s3.aws.credentials.static.secret-access-key")
    String secretKey;

    @ConfigProperty(name = "products.images.bucket")
    String bucketName;

    private AmazonS3 s3Client;

    @PostConstruct
    void init() {
        var credentials = new BasicAWSCredentials(accessKey, secretKey);

        ClientConfiguration clientConfig = new ClientConfiguration();
        if (endpoint.startsWith("http://")) {
            clientConfig.setProtocol(Protocol.HTTP);
        } else {
            clientConfig.setProtocol(Protocol.HTTPS);
        }

        s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withClientConfiguration(clientConfig).withPathStyleAccessEnabled(true).build();

        if (!s3Client.doesBucketExistV2(bucketName)) {
            s3Client.createBucket(bucketName);
        }
    }

    public String uploadImage(byte[] data) {
        String key = UUID.randomUUID().toString();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType("image/jpeg"); // Basic assumption

        s3Client.putObject(bucketName, key, new ByteArrayInputStream(data), metadata);
        return key;
    }

    public byte[] downloadImage(String key) {
        try (S3Object s3Object = s3Client.getObject(bucketName, key)) {
            return IOUtils.toByteArray(s3Object.getObjectContent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to download image from S3", e);
        }
    }

    public void deleteImage(String key) {
        s3Client.deleteObject(bucketName, key);
    }
}
