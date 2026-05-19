package the.chak.ecommerce.products;

import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> minio;

    @Override
    public Map<String, String> start() {
        minio = new GenericContainer<>("minio/minio").withEnv("MINIO_ACCESS_KEY", "minioadmin")
                .withEnv("MINIO_SECRET_KEY", "minioadmin").withCommand("server /data")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

        minio.start();

        String host = minio.getHost();
        Integer port = minio.getMappedPort(9000);
        String endpoint = String.format("http://%s:%d", host, port);

        return Map.of("quarkus.s3.endpoint-override", endpoint, "quarkus.s3.aws.region",
                "us-east-1", "quarkus.s3.aws.credentials.static.access-key-id", "minioadmin",
                "quarkus.s3.aws.credentials.static.secret-access-key", "minioadmin",
                "products.images.bucket", "product-images");
    }

    @Override
    public void stop() {
        if (minio != null) {
            minio.stop();
        }
    }
}
