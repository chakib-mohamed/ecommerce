package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.control.PricingApiClient;
import the.chak.ecommerce.orders.control.ProductsApiClient;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * Proves the order confirm write-path now goes through the transactional outbox: confirming an order
 * commits the order document (status CONFIRMED) and a matching {@code order-initiated} outbox entry,
 * and the relay publishes it to {@code order-initiated} keyed by the order id. The keying assertion
 * guards against a lingering dual-write - the old {@code orderEmitter.send(orderDTO)} path in the
 * resource published with a {@code null} key, so any message carrying the order id that is
 * <em>not</em> keyed by it means the old fire-and-forget path still fires.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OrderOutboxWritePathTest {

    @InjectMock
    ProductsApiClient productsApiClient;

    @InjectMock
    @RestClient
    PricingApiClient pricingApi;

    @Inject
    OrderRepository orderRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @TestSecurity(user = "outbox_user")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "outbox_user") })
    @DisplayName("Confirming an order commits the CONFIRMED order and an order-initiated outbox entry that the relay publishes keyed by the order id")
    void confirmOrder_writesOrderInitiatedOutboxEntry_publishedKeyedByOrderId() {
        // given - an initiated order owned by the caller
        Order order = new Order();
        order.setUserID("outbox_user");
        order.setStatus(OrderStatus.INITIATED);
        orderRepository.persist(order);
        String orderId = order.id.toString();
        double confirmedBefore = orderConfirmedCount();

        try (KafkaConsumer<String, String> consumer = newConsumer("order-initiated")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when
            given().when().post("/orders/" + orderId + "/confirm")
                    .then().statusCode(200);

            // then - the order is committed as CONFIRMED
            Order saved = orderRepository.findById(order.id);
            assertEquals(OrderStatus.CONFIRMED, saved.getStatus());

            // and - an orders-confirmed metric is recorded for the confirm
            assertEquals(confirmedBefore + 1.0, orderConfirmedCount(), 0.001);

            // and - exactly the relay's keyed message lands; no unkeyed dual-write copy
            List<ConsumerRecord<String, String>> records =
                    drainByValue(consumer, orderId, Duration.ofSeconds(15));
            assertFalse(records.isEmpty(), "an order-initiated message should be published on confirm");
            assertAllKeyedBy(records, orderId);

            // and - a matching outbox entry was committed and is eventually stamped published
            List<OutboxEntry> entries = outboxEntries(orderId);
            assertEquals(1, entries.size(), "exactly one order-initiated outbox entry for the confirmed order");
            assertEquals("order", entries.get(0).aggregateType);
            assertTrue(entries.get(0).payload.contains(orderId), "payload should carry the order id");
            awaitPublished(orderId);
        }
    }

    // --- helpers -----------------------------------------------------------

    private double orderConfirmedCount() {
        var counter = meterRegistry.find("orders.confirmed").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private List<OutboxEntry> outboxEntries(String orderId) {
        return outboxRepository.find("aggregateId = ?1 and topic = ?2", orderId, "order-initiated").list();
    }

    private void awaitPublished(String orderId) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            boolean published = outboxEntries(orderId).stream().anyMatch(e -> e.publishedAt != null);
            if (published) {
                return;
            }
            sleep(200);
        }
        throw new AssertionError("outbox entry for " + orderId + " was never stamped published");
    }

    private static void assertAllKeyedBy(List<ConsumerRecord<String, String>> records, String orderId) {
        assertTrue(records.stream().allMatch(r -> orderId.equals(r.key())),
                "every message carrying the order id must be keyed by it - an unkeyed message means a dual-write still fires");
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-outbox-write-path-test-" + UUID.randomUUID());
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
