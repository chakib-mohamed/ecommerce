package the.chak.ecommerce.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.pricing.control.PriceService;
import the.chak.ecommerce.pricing.entity.OutboxEntry;
import the.chak.ecommerce.pricing.entity.Price;
import the.chak.ecommerce.pricing.repository.OutboxRepository;
import the.chak.ecommerce.pricing.repository.PriceRepository;

/**
 * Proves the write-path now goes through the transactional outbox: an update commits the price
 * document and a matching {@code outbox} entry in the same Mongo transaction, and the relay
 * publishes it to {@code price-changed} keyed by the product id. The key assertion guards against a
 * lingering dual-write - the old {@code emitter.send(event)} path published with a {@code null} key,
 * so any message carrying the product id that is <em>not</em> keyed by it means the old path fires.
 */
@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PriceOutboxWritePathTest {

    @Inject
    PriceService priceService;

    @Inject
    PriceRepository priceRepository;

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("Updating a price commits the price document and a price-changed outbox entry that the relay publishes keyed by the product id")
    void updatePrice_writesPriceChangedOutboxEntry_publishedKeyedByProductId() {
        String productId = UUID.randomUUID().toString();

        try (KafkaConsumer<String, String> consumer = newConsumer("price-changed")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            priceService.update(productId, 42.0);

            // then - the price document is committed
            Price saved = priceRepository.find("productId", productId).firstResult();
            assertNotNull(saved, "the price document should be persisted");
            assertEquals(42.0, saved.price, 0.001);

            // and - exactly the relay's keyed message lands; no unkeyed dual-write copy
            List<ConsumerRecord<String, String>> records =
                    drainByValue(consumer, productId, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "a price-changed message should be published for the update");
            assertAllKeyedBy(records, productId);

            // and - a matching outbox entry was committed and is eventually stamped published
            List<OutboxEntry> entries = outboxEntries(productId);
            assertEquals(1, entries.size(), "exactly one price-changed outbox entry for the updated price");
            assertEquals("price", entries.get(0).aggregateType);
            assertTrue(entries.get(0).payload.contains(productId), "payload should carry the product id");
            awaitPublished(productId);
        }
    }

    // --- helpers -----------------------------------------------------------

    private List<OutboxEntry> outboxEntries(String productId) {
        return outboxRepository.find("aggregateId = ?1 and topic = ?2", productId, "price-changed").list();
    }

    private void awaitPublished(String productId) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            boolean published = outboxEntries(productId).stream().anyMatch(e -> e.publishedAt != null);
            if (published) {
                return;
            }
            sleep(200);
        }
        throw new AssertionError("outbox entry for " + productId + " was never stamped published");
    }

    private static void assertAllKeyedBy(List<ConsumerRecord<String, String>> records, String productId) {
        assertTrue(records.stream().allMatch(r -> productId.equals(r.key())),
                "every message carrying the product id must be keyed by it - an unkeyed message means a dual-write still fires");
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "price-outbox-write-path-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /**
     * Collects every record whose value carries the needle over the window. Keeps polling for a short
     * grace period after the first hit so a stray unkeyed dual-write copy would also be caught.
     */
    private List<ConsumerRecord<String, String>> drainByValue(
            KafkaConsumer<String, String> consumer, String needle, Duration timeout) {
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long firstMatchAt = -1;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(needle)) {
                    matched.add(record);
                }
            }
            if (!matched.isEmpty()) {
                long now = System.currentTimeMillis();
                if (firstMatchAt < 0) {
                    firstMatchAt = now;
                } else if (now - firstMatchAt > 1500) {
                    break;
                }
            }
        }
        return matched;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
