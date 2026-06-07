package the.chak.ecommerce.products;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    static KafkaContainer container =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withReuse(true);

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
            createTopic("product-updated");
            createTopic("product-deleted");
            createTopic("product-updated-dlq");
            createTopic("product-deleted-dlq");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    private void createTopic(String topic) throws Exception {
        container.execInContainer("kafka-topics", "--create", "--bootstrap-server", "localhost:9092",
                "--topic", topic, "--if-not-exists", "--partitions", "1", "--replication-factor", "1")
                .getExitCode();
    }

    @Override
    public void stop() {
    }
}
