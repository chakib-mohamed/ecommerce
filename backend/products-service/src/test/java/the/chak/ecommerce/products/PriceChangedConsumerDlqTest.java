package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.ProductRepository;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PriceChangedConsumerDlqTest {

    @Inject
    ProductRepository productRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("A poison price-changed message is routed to price-changed-dlq while valid messages keep flowing")
    void poisonMessage_routedToDlq_partitionKeepsFlowing() {
        // given - a product that a subsequent valid price-changed message will update
        UUID uuid = QuarkusTransaction.requiringNew().call(() -> {
            Product product = new Product();
            product.setTitle("DLQ Test Product");
            product.setDescription("desc");
            product.setPrice(100.0);
            productRepository.persist(product);
            return product.getUuid();
        });

        String poison = "this-is-not-valid-json-" + uuid;
        // On-the-wire payload is snake_case (price-service serializes with the shared
        // LOWER_CASE_WITH_UNDERSCORES JSON-B strategy, and this consumer deserializes with the same).
        String valid = "{\"product_id\":\"" + uuid + "\",\"new_price\":42.0}";

        try (KafkaConsumer<String, String> dlq = newConsumer("price-changed-dlq")) {
            dlq.poll(Duration.ofMillis(500)); // force partition assignment

            // when - a poison message then a valid message are sent to the same topic/key
            try (KafkaProducer<String, String> producer = newProducer()) {
                producer.send(new ProducerRecord<>("price-changed", uuid.toString(), poison));
                producer.send(new ProducerRecord<>("price-changed", uuid.toString(), valid));
                producer.flush();
            }

            // then - the poison message is isolated on the dead-letter topic
            List<ConsumerRecord<String, String>> dead =
                    drain(dlq, uuid.toString(), Duration.ofSeconds(20));
            assertFalse(dead.isEmpty(), "poison message should be routed to price-changed-dlq");
            assertTrue(dead.get(0).value().contains(poison),
                    "the dead-lettered record should carry the original poison payload");
        }

        // and - the partition keeps flowing: the valid message updates the stored price
        assertEquals(42.0, awaitPrice(uuid, Duration.ofSeconds(20)), 0.001,
                "a valid message after the poison one must still be processed");
    }

    // --- helpers -----------------------------------------------------------

    private double awaitPrice(UUID uuid, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Double price = null;
        while (System.currentTimeMillis() < deadline) {
            price = QuarkusTransaction.requiringNew().call(() -> {
                Product p = productRepository.<Product>find("uuid", uuid).firstResult();
                return p == null ? null : p.getPrice();
            });
            if (price != null && price == 42.0) {
                return price;
            }
            sleep(250);
        }
        return price == null ? Double.NaN : price;
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "price-changed-dlq-test-" + UUID.randomUUID());
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
