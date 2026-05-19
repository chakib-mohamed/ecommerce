package the.chak.ecommerce.pricing;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    static KafkaContainer container =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Override
    public Map<String, String> start() {
        container.start();
        return Map.of("kafka.bootstrap.servers", container.getBootstrapServers());
    }

    @Override
    public void stop() {
        container.stop();
    }
}
