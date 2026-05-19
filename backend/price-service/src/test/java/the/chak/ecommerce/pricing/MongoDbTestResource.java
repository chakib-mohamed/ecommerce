package the.chak.ecommerce.pricing;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoDbTestResource implements QuarkusTestResourceLifecycleManager {

    static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Override
    public Map<String, String> start() {
        container.start();
        return Map.of("quarkus.mongodb.connection-string", container.getConnectionString());
    }

    @Override
    public void stop() {
        container.stop();
    }
}
