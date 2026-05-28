package the.chak.ecommerce.products.control;

import java.net.URI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@ApplicationScoped
public class S3ClientProducer {

    @ConfigProperty(name = "products.storage.endpoint")
    String endpoint;

    @ConfigProperty(name = "products.storage.region")
    String region;

    @ConfigProperty(name = "products.storage.access-key")
    String accessKey;

    @ConfigProperty(name = "products.storage.secret-key")
    String secretKey;

    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
