package the.chak.ecommerce.payment;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** A real broker for the tests that need one, matching the other services' setup. */
public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    static KafkaContainer container =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withReuse(true);

    @Override
    public Map<String, String> start() {
        if (!container.isRunning()) {
            container.start();
        }
        return Map.of("kafka.bootstrap.servers", container.getBootstrapServers());
    }

    @Override
    public void stop() {
    }
}
