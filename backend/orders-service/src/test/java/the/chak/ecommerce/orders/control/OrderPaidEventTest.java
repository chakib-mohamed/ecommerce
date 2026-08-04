package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.entity.ProductVO;

/**
 * The event that tells the rest of the platform money was actually taken.
 *
 * <p>It exists because analytics counts revenue, and until now the only order event it could count
 * was the confirmation - so a declined card that was cancelled seconds later still registered as
 * money. What matters here is that the event carries enough to be counted (the lines, their prices
 * and their discounts) and nothing that must not travel (the payment method reference).
 */
class OrderPaidEventTest {

    private static final String PAYMENT_METHOD = "pm_card_visa";

    /**
     * Built with the same JSON-B configuration the application registers, so the payload asserted
     * here is the payload that goes on the wire. A default JsonbBuilder would emit camelCase and
     * quietly pass tests that a real consumer could not read.
     */
    private OutboxEventFactory factory() {
        OutboxEventFactory factory = new OutboxEventFactory();
        factory.jsonb = JsonbBuilder.create(new JsonbConfig()
                .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES)
                .withNullValues(false));
        return factory;
    }

    @Test
    @DisplayName("Publishes the paid order on its own topic")
    void orderPaid_goesToItsOwnTopic() {
        // given - a separate topic from order-initiated, because the two now mean different things:
        // one is an order placed, the other is money taken
        Order order = paidOrder();

        // when
        OutboxEntry entry = factory().orderPaid(order);

        // then
        assertEquals("order-paid", entry.topic);
        assertEquals("order-paid", entry.eventType);
    }

    @Test
    @DisplayName("Keys the event by order id like every other message about an order")
    void orderPaid_isKeyedByOrderId() {
        // given
        Order order = paidOrder();

        // when
        OutboxEntry entry = factory().orderPaid(order);

        // then - one order's messages have to keep their order on the broker
        assertEquals(order.id.toString(), entry.aggregateId);
    }

    @Test
    @DisplayName("Carries the lines a sale is counted from")
    void orderPaid_carriesTheOrderLines() {
        // given - the warehouse needs product, quantity, price and discount per line; without them
        // it would have to call back into orders-service, which ADR-0010 rules out
        Order order = paidOrder();

        // when
        String payload = factory().orderPaid(order).payload;

        // then
        assertTrue(payload.contains("\"product_id\":\"p1\""), payload);
        assertTrue(payload.contains("\"qty\":2"), payload);
        assertTrue(payload.contains("49.99"), payload);
        assertTrue(payload.contains("\"percentage_off\":10.0"), payload);
    }

    @Test
    @DisplayName("Reports the order as paid, not as whatever it was before")
    void orderPaid_carriesThePaidStatus() {
        // given
        Order order = paidOrder();

        // when
        String payload = factory().orderPaid(order).payload;

        // then
        assertTrue(payload.contains("\"status\":\"PAID\""), payload);
    }

    @Test
    @DisplayName("Never carries the payment method")
    void orderPaid_omitsThePaymentMethod() {
        // given - the reference is cleared when the capture resolves, but an event built from an
        // order that still held one must not publish it either
        Order order = paidOrder();
        order.setPaymentMethodRef(PAYMENT_METHOD);

        // when
        String payload = factory().orderPaid(order).payload;

        // then
        assertFalse(payload.contains(PAYMENT_METHOD),
                "a payment credential must not reach a topic every consumer reads: " + payload);
    }

    @Test
    @DisplayName("Carries the money as an amount with its currency")
    void orderPaid_carriesAmountAndCurrency() {
        // given - a revenue figure without a denomination is not a figure
        Order order = paidOrder();

        // when
        String payload = factory().orderPaid(order).payload;

        // then
        assertTrue(payload.contains("\"currency\":\"EUR\""), payload);
        assertTrue(payload.contains("89.98"), payload);
    }

    // -- helpers ------------------------------------------------------------

    private static Order paidOrder() {
        Order order = new Order();
        order.id = new ObjectId();
        order.setStatus(OrderStatus.PAID);
        order.setUserID("buyer");
        order.setPrice(new BigDecimal("89.98"));
        order.setCurrency("EUR");
        order.setCreationDate(LocalDateTime.of(2026, 1, 15, 10, 30));

        ProductVO line = new ProductVO();
        line.setProductID("p1");
        line.setTitle("Lamp");
        line.setQty(2);
        line.setPrice(new BigDecimal("49.99"));
        line.setPercentageOff(10.0);
        order.setProducts(new ArrayList<>(List.of(line)));
        return order;
    }
}
