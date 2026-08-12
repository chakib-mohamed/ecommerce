package the.chak.ecommerce.analytics.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.analytics.KafkaTestResource;

/**
 * A paid order arriving on the topic becomes revenue in the warehouse.
 *
 * <p>analytics-service is nothing but a consumer - it owns no operational data and writes nothing
 * of its own, so this path is the entire service. Everything covering it was mock-level or
 * structural: the arithmetic is unit tested, and the topic binding is pinned by reflection, but
 * nothing had ever put a message on a broker and looked in the table. Two of the four faults that
 * shipped in payment-service were of exactly that kind - a wire shape that does not bind, and a
 * consumer thread with no transaction - and neither is visible to a mock.
 *
 * <p>The message is written by hand in the shape orders-service publishes rather than serialized
 * from a class here, so a rename on this side cannot quietly rename the contract with it.
 */
@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class OrderPaidIngestionTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String brokers;

    @Inject
    EntityManager entityManager;

    @Test
    @DisplayName("Counts a paid order as revenue, line by line")
    void orderPaid_becomesRevenue() {
        // given - two lines, one of them discounted, so the arithmetic is visible in the result
        String orderId = "order-paid-" + UUID.randomUUID();
        String productA = UUID.randomUUID().toString();
        String productB = UUID.randomUUID().toString();

        // when
        send("order-paid", orderId, """
                {"id":"%s","user_id":"buyer@ecommerce.test","status":"PAID",
                 "creation_date":"2026-02-01T10:30:00","price":175.00,"currency":"EUR",
                 "products":[
                   {"product_id":"%s","title":"Oak Table","qty":2,"price":50.00},
                   {"product_id":"%s","title":"Linen Chair","qty":1,"price":100.00,
                    "percentage_off":25.0}]}
                """.formatted(orderId, productA, productB));

        // then
        assertTrue(awaitLines(orderId, 2),
                "no revenue was recorded for a paid order. The warehouse is fed by this topic and "
                        + "nothing else, so a message that does not bind, or a consumer that cannot "
                        + "reach the database, shows up as a dashboard quietly reporting no sales");

        assertEquals(0, new BigDecimal("100.00").compareTo(revenueFor(orderId, productA)),
                "2 x 50.00 with no discount should be 100.00");
        assertEquals(0, new BigDecimal("75.00").compareTo(revenueFor(orderId, productB)),
                "1 x 100.00 less 25% should be 75.00 - the discount is applied at ingestion, and "
                        + "getting it wrong overstates revenue in a way nothing else would catch");
    }

    @Test
    @DisplayName("Counts nothing for an order that was only placed")
    void orderInitiated_isNotRevenue() {
        // given - the event this warehouse used to be fed from, in the shape it had. Nothing
        // produces it any more, but a test can publish anything, and what is being pinned is
        // that a confirmation does not become revenue no matter where one comes from.
        String orderId = "order-initiated-" + UUID.randomUUID();

        // when
        send("order-initiated", orderId, """
                {"id":"%s","user_id":"buyer@ecommerce.test","status":"CONFIRMED",
                 "creation_date":"2026-02-01T10:30:00","price":100.00,"currency":"EUR",
                 "products":[{"product_id":"%s","title":"Oak Table","qty":1,"price":100.00}]}
                """.formatted(orderId, UUID.randomUUID()));

        // then - revenue means money taken. Counting a confirmed order again would put back the
        // exact bug this warehouse was changed to fix, and it would do it silently: the number
        // stays plausible, it is just no longer true. Nothing produces or consumes this topic now,
        // and this fails the day something does either.
        assertTrue(neverLines(orderId),
                "an order that was merely placed was counted as revenue");
    }

    private void send(String topic, String key, String payload) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, payload));
            producer.flush();
        }
    }

    private long countLines(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager
                .createQuery("select count(f) from FactSalesLine f where f.orderId = :id", Long.class)
                .setParameter("id", orderId)
                .getSingleResult());
    }

    /** Waits for the expected rows to appear. */
    private boolean awaitLines(String orderId, int expected) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (countLines(orderId) >= expected) {
                return true;
            }
            sleep();
        }
        return false;
    }

    /**
     * Waits and asserts nothing appears. A negative needs a window rather than a single look: the
     * consumer is asynchronous, so checking immediately would pass even if the row were on its way.
     */
    private boolean neverLines(String orderId) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (countLines(orderId) > 0) {
                return false;
            }
            sleep();
        }
        return countLines(orderId) == 0;
    }

    private BigDecimal revenueFor(String orderId, String productId) {
        List<BigDecimal> found = QuarkusTransaction.requiringNew().call(() -> entityManager
                .createQuery("select f.lineRevenue from FactSalesLine f "
                        + "where f.orderId = :order and f.productId = :product", BigDecimal.class)
                .setParameter("order", orderId)
                .setParameter("product", productId)
                .getResultList());
        assertEquals(1, found.size(), "expected exactly one line for " + productId);
        assertNotNull(found.get(0));
        return found.get(0);
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
