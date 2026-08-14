package the.chak.ecommerce.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.analytics.control.KafkaEventConsumer;

/**
 * Which event the warehouse treats as revenue.
 *
 * <p>Structural rather than behavioural on purpose. The ingestion arithmetic is unchanged and
 * already covered; the entire substance of this change is one binding - the topic the fact table is
 * filled from - and that binding is an annotation, so an annotation is what has to be pinned.
 *
 * <p>Getting it wrong is silent. Feeding the fact table from a confirmation again would count
 * every confirmed order as money taken, including ones whose card was declined, and the dashboard
 * would report a plausible number that is simply untrue. Nothing would fail, which is exactly why
 * this test exists.
 */
class RevenueSourceTest {

    private static List<String> consumedTopics() {
        return Arrays.stream(KafkaEventConsumer.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Incoming.class))
                .filter(incoming -> incoming != null)
                .map(Incoming::value)
                .toList();
    }

    private static Method orderConsumer() {
        return Arrays.stream(KafkaEventConsumer.class.getDeclaredMethods())
                .filter(method -> {
                    Incoming incoming = method.getAnnotation(Incoming.class);
                    return incoming != null && "order-paid".equals(incoming.value());
                })
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("Fills the warehouse from the order-paid event")
    void warehouse_isFedByOrderPaid() {
        // given / when / then - revenue means money taken, and this is the only event that says so
        assertTrue(consumedTopics().contains("order-paid"),
                "expected a consumer bound to order-paid, found " + consumedTopics());
    }

    @Test
    @DisplayName("No longer counts a confirmed order as a sale")
    void warehouse_isNotFedByOrderInitiated() {
        // given / when / then - a confirmation is an order placed, not money taken; counting it
        // means a declined card that was cancelled seconds later still shows up as revenue.
        // The event itself has since been deleted outright - it had no consumer left once this
        // warehouse moved off it - so this now guards against resurrecting the name as well.
        assertTrue(!consumedTopics().contains("order-initiated"),
                "order-initiated must not feed the warehouse, found " + consumedTopics());
    }

    @Test
    @DisplayName("Counts a sale from exactly one order event")
    void warehouse_hasASingleOrderSource() {
        // given - two order sources would each write the same rows, and whichever arrived last
        // would decide the figure
        List<String> orderTopics = consumedTopics().stream()
                .filter(topic -> topic.startsWith("order-"))
                .toList();

        // then
        assertEquals(List.of("order-paid"), orderTopics);
    }

    @Test
    @DisplayName("Takes the paid order as the same payload the sale was placed with")
    void orderPaidConsumer_takesTheOrderPayload() {
        // given - the fact table needs the lines, so the event has to carry the whole order rather
        // than an id the warehouse would have to resolve over REST (rejected in ADR-0010)
        Method consumer = orderConsumer();

        // then
        assertTrue(consumer != null, "no consumer bound to order-paid");
        assertEquals(1, consumer.getParameterCount());
        assertEquals(the.chak.ecommerce.orders.boundary.dto.OrderDTO.class,
                consumer.getParameterTypes()[0]);
    }
}
