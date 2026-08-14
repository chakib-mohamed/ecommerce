package the.chak.ecommerce.payment.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
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
import the.chak.ecommerce.payment.KafkaTestResource;
import the.chak.ecommerce.payment.StripeTestResource;
import the.chak.ecommerce.payment.entity.PaymentStatus;
import the.chak.ecommerce.payment.repository.PaymentRepository;

/**
 * A capture command in, a reply out, through the real broker and the real database.
 *
 * <p>This service had four faults that shipped and every one of its tests stayed green, because all
 * of them mock the collaborators away. It could not read its own tables - the entities mapped
 * camelCase columns against a snake_case schema, and nothing validated that at startup. It could not
 * read its own messages - no JSON-B customizer, so every {@code payment_method} arrived null and
 * every reply would have been published in a shape orders-service cannot parse. It could not touch
 * the database at all from a Kafka consumer thread, having no transaction or request context there.
 * And it listened on the wrong port, so the container never became healthy.
 *
 * <p>Each of those is invisible to a unit test and fatal in a running system, and three of the four
 * would have failed this test on the first run. What it asserts is deliberately end-to-end and
 * shallow: a command published to the topic produces a reply on the topic and a row in the table.
 * Everything in between - deserialization, naming, transactions, the outbox, the relay, the
 * provider call, serialization - has to work for that to happen, and none of it is asserted
 * directly, so this does not need rewriting when any of it changes shape.
 */
@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StripeTestResource.class)
@Tag("integration")
class CapturePaymentFlowTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String brokers;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    Jsonb jsonb;

    @Test
    @DisplayName("Charges a capture command and replies on payment-captured")
    void capture_replies_andRecordsTheCharge() {
        // given
        String orderId = "6512c0ffee" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String stepId = UUID.randomUUID().toString();

        // when - the wire shape the orchestrator sends, written by hand rather than serialized from
        // a class in this service, so a rename on this side cannot quietly rename the contract too
        send("capture-payment", orderId,
                "{\"order_id\":\"" + orderId + "\",\"step_id\":\"" + stepId + "\","
                        + "\"amount\":49.99,\"currency\":\"EUR\","
                        + "\"payment_method\":\"" + StripeTestResource.CARD_OK + "\"}");

        // then - a reply arrives
        String reply = awaitOne("payment-captured", "payment-captured-it", orderId);
        assertNotNull(reply, "no reply was published: the command was consumed and answered nowhere, "
                + "which from the orchestrator's side is a payment that never comes back");

        Map<?, ?> parsed = jsonb.fromJson(reply, Map.class);
        assertEquals(orderId, parsed.get("order_id"),
                "the reply is not in the shape the orchestrator reads - a field it cannot find is a "
                        + "reply it discards, and the order waits until its deadline");
        assertEquals(stepId, parsed.get("step_id"));

        // and the charge is recorded, with the provider's reference a refund would need
        var recorded = paymentRepository.findAttempt(orderId, stepId);
        assertTrue(recorded.isPresent(), "the charge was made and never written down");
        assertEquals(PaymentStatus.CAPTURED, recorded.get().getStatus());
        assertEquals(StripeTestResource.PROVIDER_REF, recorded.get().getProviderRef());
        assertEquals(0, new BigDecimal("49.99").compareTo(recorded.get().getAmount()),
                "the amount charged is not the amount asked for");
    }

    @Test
    @DisplayName("Replies on payment-failed when the provider refuses the card")
    void capture_declined_repliesFailed() {
        // given
        String orderId = "6512dec1111" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        String stepId = UUID.randomUUID().toString();

        // when
        send("capture-payment", orderId,
                "{\"order_id\":\"" + orderId + "\",\"step_id\":\"" + stepId + "\","
                        + "\"amount\":10.00,\"currency\":\"EUR\","
                        + "\"payment_method\":\"" + StripeTestResource.CARD_DECLINED + "\"}");

        // then - a refusal is an outcome and has to come back as one. Silence here looks exactly
        // like a provider outage, and the order is cancelled for a timeout instead of a decline.
        String reply = awaitOne("payment-failed", "payment-failed-it", orderId);
        assertNotNull(reply, "a declined card produced no reply at all");

        Map<?, ?> parsed = jsonb.fromJson(reply, Map.class);
        assertEquals(orderId, parsed.get("order_id"));

        var recorded = paymentRepository.findAttempt(orderId, stepId);
        assertTrue(recorded.isPresent(), "the refusal was not written down, so a redelivery would "
                + "present the same card again");
        assertEquals(PaymentStatus.FAILED, recorded.get().getStatus());
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

    /**
     * Waits for this order's message on a topic.
     *
     * <p>Matched on the order rather than taking whatever arrives first: the broker container is
     * reused between runs, so the earliest message on a topic is quite likely to be someone else's
     * from an hour ago, and a test that asserts against it fails for a reason that has nothing to
     * do with the code.
     *
     * <p>Generous, because it is waiting on a consumer, a charge, a commit and a relay poll - but it
     * waits for the message rather than for a duration.
     */
    private String awaitOne(String topic, String group, String orderId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group + "-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(orderId)) {
                        return record.value();
                    }
                }
            }
        }
        return null;
    }
}
