package the.chak.ecommerce.orders.control;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderSearch;
import the.chak.ecommerce.orders.repository.OutboxRepository;
import the.chak.ecommerce.orders.repository.PagedResult;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    ProductsApiClient productsApiClient;

    @Inject
    @RestClient
    PricingApiClient pricingApiClient;

    @Inject
    the.chak.ecommerce.orders.repository.OrderRepository orderRepository;

    @Inject
    MongoClient mongoClient;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    OutboxRelay outboxRelay;

    @Inject
    MeterRegistry meterRegistry;

    public Order saveOrder(Order order) {
        order.setCreationDate(LocalDateTime.now());
        order.setStatus(OrderStatus.INITIATED);

        order.getProducts().forEach(productVO -> {
            ProductDto product = productsApiClient.getProduct(productVO.getProductID());
            if (product == null) {
                throw new ProductNotFoundException(productVO.getProductID());
            }
            productVO.setTitle(product.getTitle());
            productVO.setPrice(product.getPrice());
            productVO.setPercentageOff(Optional.ofNullable(product.getPromotions())
                    .map(promos -> promos.stream().filter(this::isPromotionActive)
                            .collect(Collectors.toList()))
                    .map(promos -> promos.stream().map(PromotionDto::getPercentageOff).reduce(0d,
                            Double::sum))
                    .orElse(null));
        });

        OrderDTO pricingOrder = new OrderDTO();
        pricingOrder.setProducts(order.getProducts().stream().map(p -> {
            ProductVO item = new ProductVO();
            item.setProductID(p.getProductID());
            item.setQty(p.getQty());
            item.setPrice(p.getPrice());
            item.setPercentageOff(p.getPercentageOff());
            return item;
        }).toList());

        // The order id is not yet assigned (persist happens after pricing) and price-service mints
        // its own processId, so the envelope id is left unset.
        PricingRequest pricingRequest = new PricingRequest();
        pricingRequest.setOrder(pricingOrder);

        long start = System.currentTimeMillis();
        Response response = pricingApiClient.calculatePrice(pricingRequest);
        LOG.infof("POST pricing-service /pricing/calculate products=%d status=%d elapsed=%dms",
                pricingOrder.getProducts().size(), response.getStatus(),
                System.currentTimeMillis() - start);
        PricingResult result = response.readEntity(PricingResult.class);
        order.setPrice(result.getOrder().getPrice());
        order.setProcessID(result.getId());
        orderRepository.persist(order);
        meterRegistry.counter(MetricNames.ORDERS_CREATED).increment();
        DistributionSummary.builder(MetricNames.ORDER_VALUE)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(order.getPrice());
        LOG.infof("Order created orderId=%s userId=%s products=%d total=%.2f",
                order.getId(), order.getUserID(), order.getProducts().size(), order.getPrice());
        return order;
    }

    private boolean isPromotionActive(PromotionDto promotion) {
        if (promotion.getActiveFrom() == null || promotion.getActiveTo() == null) {
            return false;
        }
        var now = LocalDate.now();
        return promotion.getActiveFrom().isBefore(now) && now.isBefore(promotion.getActiveTo());
    }

    public Tuple<Long, List<Order>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        OrderSearch search = new OrderSearch(
                searchOrdersCommand.getUserID(),
                searchOrdersCommand.getOffset(),
                searchOrdersCommand.getLimit());
        PagedResult<Order> result = orderRepository.search(search);
        return new Tuple<>(result.total(), result.items());
    }

    /**
     * Confirms an order and emits an {@code order-initiated} event without the dual-write hazard:
     * the {@link Order} document (status CONFIRMED) and a matching outbox entry are committed in a
     * single Mongo transaction (replica-set required), so the event can never be lost relative to
     * the business change. {@link OutboxRelay} drains the entry to the broker; this method only
     * nudges it awake after the commit.
     */
    public Order confirmOrder(String orderId) {
        Order order = orderRepository.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        order.setStatus(OrderStatus.CONFIRMED);

        OutboxEntry outboxEntry = outboxEventFactory.orderInitiated(order);

        Order toWrite = order;
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                orderRepository.mongoCollection().replaceOne(
                        session,
                        Filters.eq("_id", toWrite.id),
                        toWrite,
                        new ReplaceOptions().upsert(true));
                outboxRepository.mongoCollection().insertOne(session, outboxEntry);
                return null;
            });
        }
        meterRegistry.counter(MetricNames.ORDERS_CONFIRMED).increment();
        LOG.infof("Order confirmed orderId=%s userId=%s", order.getId(), order.getUserID());

        // Best-effort wake-up; if it is lost the scheduled tick still drains the entry.
        outboxRelay.requestPoll();
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderRepository.findById(new org.bson.types.ObjectId(orderId)));
    }

    public void updateOrder(Order order) {
        orderRepository.persistOrUpdate(order);
    }

    public void deleteOrder(Order order) {
        orderRepository.delete(order);
        LOG.infof("Order deleted orderId=%s userId=%s", order.getId(), order.getUserID());
    }
}
