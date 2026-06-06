package the.chak.ecommerce.orders.control;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OutboxEntry;

/**
 * Builds {@link OutboxEntry} documents from order domain state. The event body is the boundary
 * {@link OrderDTO} - built here directly from the {@link Order} entity rather than via the
 * {@code OrderMapper}, because the mapper lives in the {@code boundary} package and the control
 * layer may only depend on {@code boundary.dto} (enforced by the BCE ArchUnit test).
 *
 * <p>The stored payload is serialized with a plain (camelCase) JSON-B instance purely as an internal
 * round-trip format: {@link OutboxRelay} reads it back with a matching plain JSON-B before publishing.
 * This stored form is never the Kafka wire format - the relay re-serializes the event through the
 * channel's {@code JsonbSerializer} (the CDI-managed JSON-B), so the on-the-wire payload is snake_case
 * like the rest of the platform.
 */
@ApplicationScoped
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE_ORDER = "order";
    static final String TOPIC_ORDER_INITIATED = "order-initiated";

    private static final Logger LOG = Logger.getLogger(OutboxEventFactory.class);

    private Jsonb jsonb;

    @PostConstruct
    void init() {
        jsonb = JsonbBuilder.create();
    }

    @PreDestroy
    void close() {
        try {
            jsonb.close();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to close JSON-B");
        }
    }

    public OutboxEntry orderInitiated(Order order) {
        String orderId = order.id.toString();
        OutboxEntry entry = new OutboxEntry();
        entry.id = UUID.randomUUID();
        entry.aggregateType = AGGREGATE_TYPE_ORDER;
        entry.aggregateId = orderId;
        entry.eventType = TOPIC_ORDER_INITIATED;
        entry.topic = TOPIC_ORDER_INITIATED;
        entry.payload = jsonb.toJson(toDto(order, orderId));
        entry.createdAt = Instant.now();
        return entry;
    }

    private OrderDTO toDto(Order order, String orderId) {
        OrderDTO dto = new OrderDTO();
        dto.setId(orderId);
        dto.setCreationDate(order.getCreationDate());
        dto.setPrice(order.getPrice());
        dto.setUserID(order.getUserID());
        dto.setValidationNumber(order.getValidationNumber());
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
