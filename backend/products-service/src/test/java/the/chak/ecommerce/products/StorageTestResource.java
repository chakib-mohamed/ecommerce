package the.chak.ecommerce.products;

import java.util.Map;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class StorageTestResource implements QuarkusTestResourceLifecycleManager {

    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4"))
            .withServices(Service.S3);

    @Override
    public Map<String, String> start() {
        localstack.start();
        return Map.of(
                "products.storage.endpoint",
                localstack.getEndpointOverride(Service.S3).toString(),
                "products.storage.region", localstack.getRegion(),
                "products.storage.access-key", localstack.getAccessKey(),
                "products.storage.secret-key", localstack.getSecretKey(),
                "products.images.bucket", "product-images");
    }

    @Override
    public void stop() {
        localstack.stop();
    }
}
