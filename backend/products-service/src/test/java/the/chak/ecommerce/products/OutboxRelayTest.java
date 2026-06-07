package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.control.OutboxRelay;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OutboxRelayTest {

    @Inject
    OutboxRelay relay;

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("A relay tick publishes an unpublished row keyed by its aggregate id and stamps published_at")
    void relayTick_publishesUnpublishedRow_keyedByAggregateId_andStampsPublishedAt() {
        // given
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"product\":{\"uuid\":\"" + aggregateId + "\"}}";
        UUID rowId = insertUnpublished("product-updated", aggregateId, payload);

        try (KafkaConsumer<String, String> consumer = newConsumer("product-updated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - the message lands on the topic, keyed by the aggregate id
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, aggregateId.toString(), Duration.ofSeconds(15));
            assertEquals(1, records.size(), "exactly one message should be published for the row");
            assertEquals(aggregateId.toString(), records.get(0).key());
            assertTrue(records.get(0).value().contains(aggregateId.toString()),
                    "published payload should carry the aggregate id");
        }

        // and - the row is stamped as published so it is never re-sent
        OutboxEvent reloaded = findById(rowId);
        assertNotNull(reloaded.getPublishedAt(), "published_at must be stamped after a successful send");
    }

    @Test
    @DisplayName("A relay tick does not re-publish a row that is already marked published")
    void relayTick_doesNotRepublish_alreadyPublishedRow() {
        // given - a row that is already published
        UUID aggregateId = UUID.randomUUID();
        insertPublished("product-updated", aggregateId, "{\"product\":{\"uuid\":\"" + aggregateId + "\"}}");

        try (KafkaConsumer<String, String> consumer = newConsumer("product-updated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - nothing for this aggregate is published
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, aggregateId.toString(), Duration.ofSeconds(5));
            assertTrue(records.isEmpty(), "an already-published row must not be re-sent");
        }
    }

    @Test
    @DisplayName("requestPoll() publishes a freshly inserted row without waiting for the scheduled timer")
    void requestPoll_publishesFreshRow_withoutWaitingForTimer() {
        // given
        UUID aggregateId = UUID.randomUUID();
        UUID rowId = insertUnpublished("product-deleted", aggregateId, "{\"product_uuid\":\"" + aggregateId + "\"}");

        try (KafkaConsumer<String, String> consumer = newConsumer("product-deleted")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when - the async wake-up, not the timer (parked at 24h in test config)
            relay.requestPoll();

            // then
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, aggregateId.toString(), Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "requestPoll() should trigger publication of the fresh row");
            assertEquals(aggregateId.toString(), records.get(0).key());
        }

        OutboxEvent reloaded = findById(rowId);
        assertNotNull(reloaded.getPublishedAt());
    }

    // --- helpers -----------------------------------------------------------

    private UUID insertUnpublished(String topic, UUID aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload(payload);
        QuarkusTransaction.requiringNew().run(() -> outboxRepository.persist(event));
        return event.getId();
    }

    private void insertPublished(String topic, UUID aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("product");
        event.setAggregateId(aggregateId);
        event.setEventType(topic);
        event.setTopic(topic);
        event.setPayload(payload);
        event.setPublishedAt(Instant.now());
        QuarkusTransaction.requiringNew().run(() -> outboxRepository.persist(event));
    }

    private OutboxEvent findById(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> outboxRepository.findById(id));
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-relay-test-" + UUID.randomUUID());
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
