package the.chak.ecommerce.products;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    static KafkaContainer container =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Override
    public Map<String, String> start() {
        if (!container.isRunning()) {
            container.start();
            createTopics();
        }
        String servers = container.getBootstrapServers();
        return Map.of(
                "kafka.bootstrap.servers", servers,
                "mp.messaging.connector.smallrye-kafka.bootstrap.servers", servers
        );
    }

    private void createTopics() {
        try {
            container.execInContainer("kafka-topics", "--create", "--bootstrap-server", "localhost:9092",
                    "--topic", "product-updated", "--if-not-exists", "--partitions", "1", "--replication-factor", "1")
                    .getExitCode();
            container.execInContainer("kafka-topics", "--create", "--bootstrap-server", "localhost:9092",
                    "--topic", "product-deleted", "--if-not-exists", "--partitions", "1", "--replication-factor", "1")
                    .getExitCode();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    @Override
    public void stop() {
    }
}
