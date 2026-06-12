package the.chak.ecommerce.pricing.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.opentelemetry.context.Context;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PriceChangedWireFormatTest {

    @Inject
    KafkaPriceEventPublisher publisher;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("The price-changed payload is serialized snake_case on the wire (product_id / new_price)")
    void priceChangedPayload_isSnakeCaseOnTheWire() {
        // given
        UUID productId = UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = newConsumer("price-changed")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when - the real producer path serializes the event onto the topic
            publisher.publishPriceChanged(new PriceChangedEvent(productId.toString(), 42.0),
                    productId.toString(), Context.root());

            // then - the wire payload uses snake_case field names, never camelCase
            String wire = awaitWire(consumer, productId.toString(), Duration.ofSeconds(20));
            assertNotNull(wire, "a price-changed record carrying the product id should be published");
            assertTrue(wire.contains("\"product_id\""), "wire payload must contain snake_case product_id: " + wire);
            assertTrue(wire.contains("\"new_price\""), "wire payload must contain snake_case new_price: " + wire);
            assertFalse(wire.contains("\"productId\""), "wire payload must not contain camelCase productId: " + wire);
            assertFalse(wire.contains("\"newPrice\""), "wire payload must not contain camelCase newPrice: " + wire);
        }
    }

    // --- helpers -----------------------------------------------------------

    private String awaitWire(KafkaConsumer<String, String> consumer, String mustContain, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(mustContain)) {
                    return record.value();
                }
            }
        }
        return null;
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "price-changed-wire-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
