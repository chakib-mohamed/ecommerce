package the.chak.ecommerce.pricing;

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
import the.chak.ecommerce.pricing.control.OutboxRelay;
import the.chak.ecommerce.pricing.entity.OutboxEntry;
import the.chak.ecommerce.pricing.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
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
    @DisplayName("A relay tick publishes an unpublished entry keyed by its product id and stamps the published timestamp")
    void relayTick_publishesUnpublishedEntry_keyedByProductId_andStampsPublishedAt() {
        // given
        String productId = UUID.randomUUID().toString();
        UUID entryId = insertUnpublished(productId, 42.0);

        try (KafkaConsumer<String, String> consumer = newConsumer("price-changed")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - the message lands on the topic, keyed by the product id
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, productId, Duration.ofSeconds(15));
            assertEquals(1, records.size(), "exactly one message should be published for the entry");
            assertEquals(productId, records.get(0).key());
            assertTrue(records.get(0).value().contains(productId),
                    "published payload should carry the product id");
        }

        // and - the entry is stamped as published so it is never re-sent
        OutboxEntry reloaded = outboxRepository.findById(entryId);
        assertNotNull(reloaded.publishedAt, "published timestamp must be stamped after a successful send");
    }

    @Test
    @DisplayName("A relay tick does not re-publish an entry that is already marked published")
    void relayTick_doesNotRepublish_alreadyPublishedEntry() {
        // given - an entry that is already published
        String productId = UUID.randomUUID().toString();
        insertPublished(productId, 42.0);

        try (KafkaConsumer<String, String> consumer = newConsumer("price-changed")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            relay.pollAndPublish();

            // then - nothing for this product is published
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, productId, Duration.ofSeconds(5));
            assertTrue(records.isEmpty(), "an already-published entry must not be re-sent");
        }
    }

    @Test
    @DisplayName("requestPoll() publishes a freshly inserted entry without waiting for the scheduled timer")
    void requestPoll_publishesFreshEntry_withoutWaitingForTimer() {
        // given
        String productId = UUID.randomUUID().toString();
        UUID entryId = insertUnpublished(productId, 42.0);

        try (KafkaConsumer<String, String> consumer = newConsumer("price-changed")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when - the async wake-up, not the timer (parked at 24h in test config)
            relay.requestPoll();

            // then
            List<ConsumerRecord<String, String>> records =
                    drain(consumer, productId, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "requestPoll() should trigger publication of the fresh entry");
            assertEquals(productId, records.get(0).key());
        }

        OutboxEntry reloaded = outboxRepository.findById(entryId);
        assertNotNull(reloaded.publishedAt);
    }

    // --- helpers -----------------------------------------------------------

    private UUID insertUnpublished(String productId, double newPrice) {
        OutboxEntry entry = newEntry(productId, newPrice);
        outboxRepository.persist(entry);
        return entry.id;
    }

    private void insertPublished(String productId, double newPrice) {
        OutboxEntry entry = newEntry(productId, newPrice);
        entry.publishedAt = Instant.now();
        outboxRepository.persist(entry);
    }

    private OutboxEntry newEntry(String productId, double newPrice) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = "price";
        entry.aggregateId = productId;
        entry.eventType = "price-changed";
        entry.topic = "price-changed";
        entry.payload = "{\"product_id\":\"" + productId + "\",\"new_price\":" + newPrice + "}";
        entry.createdAt = Instant.now();
        return entry;
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
