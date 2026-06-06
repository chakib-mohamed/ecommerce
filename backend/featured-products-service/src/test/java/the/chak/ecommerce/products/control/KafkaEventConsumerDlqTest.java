package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.MongoDbTestResource;
import the.chak.ecommerce.products.entity.ProductMongoEntity;
import the.chak.ecommerce.products.repository.ProductMongoRepository;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class KafkaEventConsumerDlqTest {

    @Inject
    ProductMongoRepository productMongoRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @BeforeEach
    void setup() {
        productMongoRepository.deleteAll();
    }

    @Test
    @DisplayName("A poison product-updated message is routed to product-updated-dlq while valid messages keep flowing")
    void poisonProductUpdated_routedToDlq_partitionKeepsFlowing() {
        // given
        UUID uuid = UUID.randomUUID();
        String poison = "not-a-product-updated-event-" + uuid;
        String valid = "{\"product\":{\"uuid\":\"" + uuid + "\"}}";

        try (KafkaConsumer<String, String> dlq = newConsumer("product-updated-dlq")) {
            dlq.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            try (KafkaProducer<String, String> producer = newProducer()) {
                producer.send(new ProducerRecord<>("product-updated", uuid.toString(), poison));
                producer.send(new ProducerRecord<>("product-updated", uuid.toString(), valid));
                producer.flush();
            }

            // then - poison isolated on the DLQ
            List<ConsumerRecord<String, String>> dead =
                    drain(dlq, uuid.toString(), Duration.ofSeconds(20));
            assertFalse(dead.isEmpty(), "poison message should be routed to product-updated-dlq");
            assertTrue(dead.get(0).value().contains(poison));
        }

        // and - the valid message after the poison one is still processed
        assertNotNull(awaitPresent(uuid, Duration.ofSeconds(20)),
                "a valid product-updated message after a poison one must still be processed");
    }

    @Test
    @DisplayName("A poison product-deleted message is routed to product-deleted-dlq while valid messages keep flowing")
    void poisonProductDeleted_routedToDlq_partitionKeepsFlowing() {
        // given - an existing read-store entity a valid delete will remove
        UUID uuid = UUID.randomUUID();
        ProductMongoEntity entity = new ProductMongoEntity();
        entity.setProductID(uuid);
        entity.setDescription("Delete Me");
        productMongoRepository.persist(entity);

        String poison = "not-a-product-deleted-event-" + uuid;
        // On-the-wire payload is snake_case (producer serializes with the shared
        // LOWER_CASE_WITH_UNDERSCORES JSON-B strategy): productUuid -> product_uuid.
        String valid = "{\"product_uuid\":\"" + uuid + "\"}";

        try (KafkaConsumer<String, String> dlq = newConsumer("product-deleted-dlq")) {
            dlq.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            try (KafkaProducer<String, String> producer = newProducer()) {
                producer.send(new ProducerRecord<>("product-deleted", uuid.toString(), poison));
                producer.send(new ProducerRecord<>("product-deleted", uuid.toString(), valid));
                producer.flush();
            }

            // then - poison isolated on the DLQ
            List<ConsumerRecord<String, String>> dead =
                    drain(dlq, uuid.toString(), Duration.ofSeconds(20));
            assertFalse(dead.isEmpty(), "poison message should be routed to product-deleted-dlq");
            assertTrue(dead.get(0).value().contains(poison));
        }

        // and - the valid delete after the poison one is still processed
        assertNull(awaitAbsent(uuid, Duration.ofSeconds(20)),
                "a valid product-deleted message after a poison one must still be processed");
    }

    // --- helpers -----------------------------------------------------------

    private ProductMongoEntity awaitPresent(UUID uuid, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        ProductMongoEntity found = null;
        while (System.currentTimeMillis() < deadline) {
            found = productMongoRepository.findByUuid(uuid);
            if (found != null) {
                return found;
            }
            sleep(250);
        }
        return found;
    }

    private ProductMongoEntity awaitAbsent(UUID uuid, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        ProductMongoEntity found = productMongoRepository.findByUuid(uuid);
        while (System.currentTimeMillis() < deadline && found != null) {
            sleep(250);
            found = productMongoRepository.findByUuid(uuid);
        }
        return found;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private KafkaProducer<String, String> newProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "featured-dlq-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private List<ConsumerRecord<String, String>> drain(
            KafkaConsumer<String, String> consumer, String key, Duration timeout) {
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    matched.add(record);
                }
            }
            if (!matched.isEmpty()) {
                break;
            }
        }
        return matched;
    }
}
