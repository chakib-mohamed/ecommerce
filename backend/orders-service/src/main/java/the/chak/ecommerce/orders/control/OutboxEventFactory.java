package the.chak.ecommerce.orders.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.control.events.CapturePaymentCommand;
import the.chak.ecommerce.orders.control.events.OrderCancelledEvent;
import the.chak.ecommerce.orders.control.events.ReleaseStockCommand;
import the.chak.ecommerce.orders.control.events.ReserveStockCommand;
import the.chak.ecommerce.orders.control.events.ReserveStockLine;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.outbox.OutboxTracing;

/**
 * Builds {@link OutboxEntry} documents from order domain state. The event body is the boundary
 * {@link OrderDTO} - built here directly from the {@link Order} entity rather than via the
 * {@code OrderMapper}, because the mapper lives in the {@code boundary} package and the control
 * layer may only depend on {@code boundary.dto} (enforced by the BCE ArchUnit test).
 *
 * <p>The stored payload is serialized with the CDI-managed JSON-B - the same instance the Kafka
 * channel's {@code JsonbSerializer} uses - so the at-rest form is already snake_case, identical to
 * the wire. {@link OutboxRelay} reads it back with that same JSON-B before publishing; there is no
 * separate internal format.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_ORDER = "order";
    static final String TOPIC_ORDER_INITIATED = "order-initiated";
    static final String TOPIC_RESERVE_STOCK = "reserve-stock";
    static final String TOPIC_RELEASE_STOCK = "release-stock";
    static final String TOPIC_ORDER_CANCELLED = "order-cancelled";
    static final String TOPIC_CAPTURE_PAYMENT = "capture-payment";
    static final String TOPIC_ORDER_PAID = "order-paid";

    @Inject
    Jsonb jsonb;

    public OutboxEntry orderInitiated(Order order) {
        String orderId = order.id.toString();
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = AGGREGATE_TYPE_ORDER;
        entry.aggregateId = orderId;
        entry.eventType = TOPIC_ORDER_INITIATED;
        entry.topic = TOPIC_ORDER_INITIATED;
        entry.payload = jsonb.toJson(toDto(order, orderId));
        entry.traceparent = OutboxTracing.currentTraceparent();
        entry.createdAt = Instant.now();
        return entry;
    }

    /**
     * Asks the catalog to hold this order's lines. Keyed by order id like every other entry, so the
     * command and any later compensation for the same order stay in sequence on the broker.
     */
    public OutboxEntry reserveStock(Order order, String stepId) {
        String orderId = order.id.toString();
        List<ReserveStockLine> lines = order.getProducts() == null ? List.of()
                : order.getProducts().stream()
                        .map(p -> new ReserveStockLine(p.getProductID(), p.getQty()))
                        .collect(Collectors.toList());
        return build(orderId, TOPIC_RESERVE_STOCK,
                new ReserveStockCommand(orderId, stepId, lines));
    }

    public OutboxEntry releaseStock(String orderId, String stepId) {
        return build(orderId, TOPIC_RELEASE_STOCK, new ReleaseStockCommand(orderId, stepId));
    }

    /**
     * Asks for the order to be charged. Carries the payment method reference, which is why this
     * command's payload is the one thing in the outbox that is not safe to log.
     */
    public OutboxEntry capturePayment(Order order, String stepId, String paymentMethod) {
        String orderId = order.id.toString();
        return build(orderId, TOPIC_CAPTURE_PAYMENT, new CapturePaymentCommand(
                orderId, stepId, order.getPrice(), order.getCurrency(), paymentMethod));
    }

    /**
     * Announces that the order has been paid for. Distinct from {@code order-initiated}: that one
     * says an order was placed, this one says money was taken, and only this one is revenue.
     */
    public OutboxEntry orderPaid(Order order) {
        String orderId = order.id.toString();
        // The same OrderDTO order-initiated carries, and for the same reason: the warehouse counts
        // per line, so the lines have to travel with the event rather than be fetched back.
        return build(orderId, TOPIC_ORDER_PAID, toDto(order, orderId));
    }

    public OutboxEntry orderCancelled(Order order, String reason) {
        String orderId = order.id.toString();
        return build(orderId, TOPIC_ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, order.getUserID(), reason));
    }

    private OutboxEntry build(String orderId, String topic, Object payload) {
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = AGGREGATE_TYPE_ORDER;
        entry.aggregateId = orderId;
        entry.eventType = topic;
        entry.topic = topic;
        entry.payload = jsonb.toJson(payload);
        entry.traceparent = OutboxTracing.currentTraceparent();
        entry.createdAt = Instant.now();
        return entry;
    }

    private OrderDTO toDto(Order order, String orderId) {
        OrderDTO dto = new OrderDTO();
        dto.setId(orderId);
        dto.setCreationDate(order.getCreationDate());
        dto.setPrice(order.getPrice());
        // Read from the order rather than left to the DTO's field default: they agree today
        // because there is one currency, and a published amount should not depend on that.
        if (order.getCurrency() != null) {
            dto.setCurrency(order.getCurrency());
        }
        dto.setUserID(order.getUserID());
        dto.setStatus(order.getStatus() == null ? null
                : the.chak.ecommerce.orders.boundary.dto.OrderStatus.valueOf(order.getStatus().name()));
        dto.setProducts(toProductDtos(order));
        return dto;
    }

    private List<ProductVO> toProductDtos(Order order) {
        if (order.getProducts() == null) {
            return null;
        }
        return order.getProducts().stream().map(p -> {
            ProductVO vo = new ProductVO();
            vo.setProductID(p.getProductID());
            vo.setTitle(p.getTitle());
            vo.setQty(p.getQty());
            vo.setPrice(p.getPrice());
            vo.setPercentageOff(p.getPercentageOff());
            return vo;
        }).collect(Collectors.toList());
    }
}
