package the.chak.ecommerce.orders.boundary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
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
import the.chak.ecommerce.orders.control.OutboxEventFactory;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OrderPaidWireFormatTest {

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
    OutboxEventFactory outboxEventFactory;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    @DisplayName("The order-paid payload is serialized snake_case on the wire (user_id)")
    void orderPaidPayload_isSnakeCaseOnTheWire() {
        // given - a paid order whose user id is a multi-word field on the DTO
        Order order = new Order();
        order.setUserID("wire_user");
        order.setStatus(OrderStatus.PAID);
        order.setPrice(java.math.BigDecimal.valueOf(12.34));
        order.setProducts(java.util.List.of());
        orderRepository.persist(order);

        try (KafkaConsumer<String, String> consumer = newConsumer("order-paid")) {
            consumer.poll(Duration.ofMillis(500)); // force partition assignment

            // when - the relay drains an order-paid entry from the outbox. Written directly rather
            // than by driving the saga: reaching PAID needs products-service and payment-service to
            // answer, and neither runs here. What is under test is the serialization between the
            // outbox and the broker, which is the same either way.
            outboxRepository.persist(outboxEventFactory.orderPaid(order));

            // then - the wire payload uses snake_case field names, never camelCase
            String wire = awaitWire(consumer, "wire_user", Duration.ofSeconds(20));
            assertNotNull(wire, "an order-paid record should be published from the outbox");
            assertTrue(wire.contains("\"user_id\""), "wire must contain snake_case user_id: " + wire);
            assertFalse(wire.contains("\"userID\""), "wire must not contain camelCase userID: " + wire);
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-paid-wire-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
