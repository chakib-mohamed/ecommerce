package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.control.OutboxRelay;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Verifies the on-the-wire format the relay emits via {@code JsonbSerializer}. The DB-stored payload
 * is already snake_case (written by the CDI-managed JSON-B); the relay re-parses it with that same
 * JSON-B and the channel serializer re-emits snake_case - so multi-word fields must appear
 * snake_case on the wire, never camelCase.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OutboxWireFormatTest {

    @Inject
    OutboxRelay relay;

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("The product-deleted payload is serialized snake_case on the wire (product_uuid)")
    void productDeletedPayload_isSnakeCaseOnTheWire() {
        // given - a row stored in the snake_case at-rest form
        UUID aggregateId = UUID.randomUUID();
        insertUnpublished("product-deleted", aggregateId, "{\"product_uuid\":\"" + aggregateId + "\"}");

        try (KafkaConsumer<String, String> consumer = newConsumer("product-deleted")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - the wire payload is snake_case
            String wire = awaitWire(consumer, aggregateId.toString(), Duration.ofSeconds(20));
            assertNotNull(wire, "a product-deleted record should be published");
            assertTrue(wire.contains("\"product_uuid\""), "wire must contain snake_case product_uuid: " + wire);
            assertFalse(wire.contains("\"productUuid\""), "wire must not contain camelCase productUuid: " + wire);
        }
    }

    @Test
    @DisplayName("The product-updated payload is serialized snake_case on the wire (image_key)")
    void productUpdatedPayload_isSnakeCaseOnTheWire() {
        // given - a row whose product carries a multi-word field (image_key)
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"product\":{\"uuid\":\"" + aggregateId + "\",\"image_key\":\"k-" + aggregateId
                + "\",\"title\":\"t\"}}";
        insertUnpublished("product-updated", aggregateId, payload);

        try (KafkaConsumer<String, String> consumer = newConsumer("product-updated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - the nested product field is snake_case on the wire
            String wire = awaitWire(consumer, aggregateId.toString(), Duration.ofSeconds(20));
            assertNotNull(wire, "a product-updated record should be published");
            assertTrue(wire.contains("\"image_key\""), "wire must contain snake_case image_key: " + wire);
            assertFalse(wire.contains("\"imageKey\""), "wire must not contain camelCase imageKey: " + wire);
        }
    }

    // --- helpers -----------------------------------------------------------

    private void insertUnpublished(String topic, UUID aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload(payload);
        QuarkusTransaction.requiringNew().run(() -> outboxRepository.persist(event));
    }

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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-wire-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
