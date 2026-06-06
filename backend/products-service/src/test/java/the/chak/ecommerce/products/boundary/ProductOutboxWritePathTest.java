package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.entity.OutboxEvent;
import the.chak.ecommerce.products.repository.OutboxRepository;

/**
 * Proves the write-path now goes through the transactional outbox: a create/update/delete over HTTP
 * commits a matching {@code outbox} row in the same transaction as the business change, and the relay
 * publishes it to the topic keyed by the product uuid. The key assertion guards against a lingering
 * dual-write - the old {@code emitter.send(event)} path published with a {@code null} key, so any
 * message carrying the uuid that is <em>not</em> keyed by it means the old path is still firing.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class ProductOutboxWritePathTest {

    static final String BASE64_IMAGE =
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private String createdProductUuid;

    @AfterEach
    void cleanup() {
        if (createdProductUuid != null) {
            given().when().delete("/products/{id}", createdProductUuid);
            createdProductUuid = null;
        }
    }

    @Test
    @DisplayName("Creating a product commits a product-updated outbox row that the relay publishes keyed by the product uuid")
    void createProduct_writesProductUpdatedOutboxRow_publishedKeyedByUuid() {
        try (KafkaConsumer<String, String> consumer = newConsumer("product-updated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            String uuid = createProduct("Outbox Create Product", 12.5);
            createdProductUuid = uuid;

            // then - exactly the relay's keyed message lands; no unkeyed dual-write copy
            List<ConsumerRecord<String, String>> records =
                    drainByValue(consumer, uuid, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "a product-updated message should be published for the create");
            assertAllKeyedBy(records, uuid);

            // and - a matching outbox row was committed and is eventually stamped published
            List<OutboxEvent> rows = outboxRows(uuid, "product-updated");
            assertEquals(1, rows.size(), "exactly one product-updated outbox row for the created product");
            assertEquals("product", rows.get(0).getAggregateType());
            assertTrue(rows.get(0).getPayload().contains(uuid), "payload should carry the product uuid");
            awaitPublished(uuid, "product-updated");
        }
    }

    @Test
    @DisplayName("Updating a product commits a product-updated outbox row that the relay publishes keyed by the product uuid")
    void updateProduct_writesProductUpdatedOutboxRow_publishedKeyedByUuid() {
        // given - an existing product (its create already published its own product-updated row)
        String uuid = createProduct("Outbox Update Product", 10.0);
        createdProductUuid = uuid;
        awaitPublished(uuid, "product-updated");

        try (KafkaConsumer<String, String> consumer = newConsumer("product-updated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            given().contentType(ContentType.JSON)
                    .body(Map.of("uuid", uuid, "title", "Outbox Updated Title",
                            "description", "updated", "price", 20.0, "image", BASE64_IMAGE))
                    .when().put("/products").then().statusCode(200);

            // then - the update lands keyed by the uuid with the new title; nothing unkeyed
            List<ConsumerRecord<String, String>> records =
                    drainByValue(consumer, uuid, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "a product-updated message should be published for the update");
            assertAllKeyedBy(records, uuid);
            assertTrue(records.stream().anyMatch(r -> r.value().contains("Outbox Updated Title")),
                    "the published payload should reflect the updated title");

            // and - an outbox row carrying the updated title was committed and gets published
            List<OutboxEvent> rows = outboxRows(uuid, "product-updated");
            assertTrue(rows.stream().anyMatch(r -> r.getPayload().contains("Outbox Updated Title")),
                    "an outbox row should carry the updated title");
            awaitPublished(uuid, "product-updated");
        }
    }

    @Test
    @DisplayName("Deleting a product commits a product-deleted outbox row that the relay publishes keyed by the product uuid")
    void deleteProduct_writesProductDeletedOutboxRow_publishedKeyedByUuid() {
        // given
        String uuid = createProduct("Outbox Delete Product", 7.0);

        try (KafkaConsumer<String, String> consumer = newConsumer("product-deleted")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            given().when().delete("/products/{id}", uuid).then().statusCode(200);

            // then
            List<ConsumerRecord<String, String>> records =
                    drainByValue(consumer, uuid, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "a product-deleted message should be published for the delete");
            assertAllKeyedBy(records, uuid);

            List<OutboxEvent> rows = outboxRows(uuid, "product-deleted");
            assertEquals(1, rows.size(), "exactly one product-deleted outbox row for the deleted product");
            assertTrue(rows.get(0).getPayload().contains(uuid), "payload should carry the product uuid");
            awaitPublished(uuid, "product-deleted");
        }
    }

    // --- helpers -----------------------------------------------------------

    private String createProduct(String title, double price) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("title", title, "description", "desc", "price", price, "image", BASE64_IMAGE))
                .when().post("/products")
                .then().statusCode(201).extract().path("uuid");
    }

    private List<OutboxEvent> outboxRows(String uuid, String topic) {
        return QuarkusTransaction.requiringNew().call(() ->
                outboxRepository.find("aggregateId = ?1 and topic = ?2", UUID.fromString(uuid), topic).list());
    }

    private void awaitPublished(String uuid, String topic) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            boolean published = outboxRows(uuid, topic).stream()
                    .anyMatch(r -> r.getPublishedAt() != null);
            if (published) {
                return;
            }
            sleep(200);
        }
        throw new AssertionError("outbox row for " + uuid + "/" + topic + " was never stamped published");
    }

    private static void assertAllKeyedBy(List<ConsumerRecord<String, String>> records, String uuid) {
        assertTrue(records.stream().allMatch(r -> uuid.equals(r.key())),
                "every message carrying the uuid must be keyed by it - an unkeyed message means a dual-write still fires");
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "product-outbox-write-path-test-" + UUID.randomUUID());
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
