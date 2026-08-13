package the.chak.ecommerce.orders.control;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.UUID;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import the.chak.ecommerce.orders.control.exceptions.ConcurrentOrderModificationException;
import the.chak.ecommerce.orders.control.exceptions.MissingPaymentMethodException;
import the.chak.ecommerce.orders.control.exceptions.OrderNotMutableException;
import the.chak.ecommerce.orders.control.exceptions.OrderPriceChangedException;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.control.exceptions.IllegalOrderTransitionException;
import the.chak.ecommerce.orders.entity.OrderStatus;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
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

    /** Mongo field backing the optimistic-locking version on an order document. */
    private static final String VERSION_FIELD = "version";

    /** A discount can take the price to zero and no further. */
    private static final double FULL_DISCOUNT = 100d;

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

    @Inject
    OrderStateMachine stateMachine;

    @Inject
    SagaService sagaService;

    /**
     * How long a saga step is worth waiting for. Reservations have no expiry of their own, so this
     * is what bounds how long a stalled step can hold stock out of sale.
     */
    @ConfigProperty(name = "orders.saga.step-timeout", defaultValue = "PT5M")
    Duration stepTimeout;

    /**
     * Prices an order and stores it. Used where the order is the only thing being written; checkout
     * has a cart to remove in the same breath and drives the three steps below itself so that the
     * two writes share a transaction.
     */
    public Order saveOrder(Order order) {
        priceOrder(order);
        orderRepository.persist(order);
        recordOrderCreated(order);
        return order;
    }

    /**
     * Fills in title, unit price and discount from the live catalog and asks price-service for the
     * total.
     *
     * <p>This is REST traffic to two other services, so it must run before any transaction is
     * opened - persistence-conventions.md forbids network I/O inside one, and holding write locks
     * across a round trip is how a slow dependency turns into a stalled database.
     */
    public void priceOrder(Order order) {
        order.setCreationDate(LocalDateTime.now());
        order.setStatus(OrderStatus.INITIATED);

        order.getProducts().forEach(productVO -> {
            ProductDto product = productsApiClient.getProduct(productVO.getProductID());
            if (product == null) {
                throw new ProductNotFoundException(productVO.getProductID());
            }
            productVO.setTitle(product.getTitle());
            productVO.setPrice(product.getPrice());
            productVO.setPercentageOff(effectiveDiscount(product));
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
    }

    /**
     * Inserts an already-priced order using the caller's session, so the write can be rolled back
     * along with whatever else that transaction is doing.
     */
    public void insertOrder(Order order, ClientSession session) {
        orderRepository.mongoCollection().insertOne(session, order);
    }

    /**
     * Counts a created order. Kept apart from the write so a caller inside a transaction can call it
     * after the commit - a counter incremented in the body would still be incremented after an
     * abort, and metrics that overcount on retry are worse than no metrics.
     */
    public void recordOrderCreated(Order order) {
        meterRegistry.counter(MetricNames.ORDERS_CREATED).increment();
        DistributionSummary.builder(MetricNames.ORDER_VALUE)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(order.getPrice().doubleValue());
        LOG.infof("Order created orderId=%s userId=%s products=%d total=%.2f",
                order.getId(), order.getUserID(), order.getProducts().size(), order.getPrice());
    }

    /**
     * The discount a product currently attracts, summing every promotion whose window is open.
     * Used both when the order is quoted and when that quote is re-checked at confirmation, so the
     * two can never disagree about what "the price" means.
     */
    private Double effectiveDiscount(ProductDto product) {
        return Optional.ofNullable(product.getPromotions())
                .map(promos -> promos.stream().filter(this::isPromotionActive)
                        .collect(Collectors.toList()))
                .map(promos -> promos.stream().map(PromotionDto::getPercentageOff).reduce(0d,
                        Double::sum))
                // Promotions stack by summing, so two generous ones can exceed the whole price.
                // Uncapped that produces a negative line total and the order is priced at it.
                .map(total -> Math.min(FULL_DISCOUNT, Math.max(0d, total)))
                .orElse(null);
    }

    private boolean isPromotionActive(PromotionDto promotion) {
        if (promotion.getActiveFrom() == null || promotion.getActiveTo() == null) {
            return false;
        }
        // Both ends are inclusive: a promotion running "from today" or "until today" is running
        // today. Exclusive comparisons silently dropped the first and last day of every promotion.
        var now = LocalDate.now();
        return !promotion.getActiveFrom().isAfter(now) && !now.isAfter(promotion.getActiveTo());
    }

    public Tuple<Long, List<Order>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        OrderSearch search = new OrderSearch(
                searchOrdersCommand.getUserID(),
                searchOrdersCommand.getProductID(),
                toEntityStatuses(searchOrdersCommand.getStatuses()),
                searchOrdersCommand.getOffset(),
                searchOrdersCommand.getLimit());
        PagedResult<Order> result = orderRepository.search(search);
        return new Tuple<>(result.total(), result.items());
    }

    /**
     * Maps the requested states onto the entity's own enum.
     *
     * <p>By name, and only for names the entity actually has: the two enums are separate types that
     * happen to agree, and a state added to one and not the other must not turn into a filter that
     * matches nothing while looking like it matched.
     */
    private static List<the.chak.ecommerce.orders.entity.OrderStatus> toEntityStatuses(
            List<the.chak.ecommerce.orders.boundary.dto.OrderStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return statuses.stream()
                .map(Enum::name)
                .map(the.chak.ecommerce.orders.entity.OrderStatus::valueOf)
                .toList();
    }

    /**
     * Confirms the order and charges it to the given payment method.
     *
     * <p>Free of the dual-write hazard: the {@link Order} document (status CONFIRMED) and the
     * command that opens the saga are committed in a single Mongo transaction (replica-set
     * required), so the command can never be lost relative to the business change that expects an
     * answer to it. {@link OutboxRelay} drains the entry to the broker; this method only nudges it
     * awake after the commit.
     *
     * @param paymentMethod opaque single-use reference from the payment provider; never card data
     */
    public Order confirmOrder(String orderId, String paymentMethod) {
        Order order = orderRepository.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        // Before anything is written. Confirming reserves stock first, so an order that could never
        // be paid for would take inventory out of sale and only fail once the capture found nothing
        // to charge against.
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new MissingPaymentMethodException();
        }
        // Guard before anything else: a second confirmation would write a second outbox entry and
        // publish the sale twice.
        stateMachine.assertCanTransition(order.getStatus(), OrderStatus.CONFIRMED);
        OrderStatus previousStatus = order.getStatus();

        // Deliberately outside the transaction below: persistence-conventions.md forbids network
        // I/O inside one, and this reads the live catalog. Only its verdict crosses into the
        // transaction.
        assertPricesUnchanged(order);

        order.setStatus(OrderStatus.CONFIRMED);

        // Held on the order because the capture is commanded only once the stock step succeeds,
        // which is long after this call returns - so something has to keep it in between. Cleared
        // the moment the capture resolves, and never put on a published event.
        order.setPaymentMethodRef(paymentMethod);

        // Confirming opens the saga's first step. The command goes out in the same transaction as
        // the status write: sent separately it could be lost after the order had already moved,
        // leaving an order waiting on a request that was never made.
        String stepId = UUID.randomUUID().toString();
        order.setSagaStepId(stepId);
        order.setStepDeadline(Instant.now().plus(stepTimeout));

        OutboxEntry reserveCommand = outboxEventFactory.reserveStock(order, stepId);

        // The status guard above is a read-then-write, so on its own two concurrent confirmations
        // could both pass it. The write is therefore conditional on the version the order carried
        // when it was read: whichever transaction commits second matches nothing and is rejected.
        // Filters.eq matches a missing field when the value is null, so an order written before
        // this field existed is handled by the same condition with no special case.
        Long readVersion = order.getVersion();
        order.setVersion(readVersion == null ? 1L : readVersion + 1);
        Bson expectedVersion = Filters.eq(VERSION_FIELD, readVersion);

        Order toWrite = order;
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                // No upsert: a confirmation updates an order, it never creates one. With upsert the
                // write would resurrect an order deleted between the read above and this commit.
                UpdateResult result = orderRepository.mongoCollection().replaceOne(
                        session,
                        Filters.and(Filters.eq("_id", toWrite.id), expectedVersion),
                        toWrite);
                if (result.getMatchedCount() == 0) {
                    // Either the order changed underneath us or it was deleted. Aborting rolls the
                    // outbox insert back with it, so no event is published for a write that lost.
                    throw new ConcurrentOrderModificationException(orderId);
                }
                outboxRepository.mongoCollection().insertOne(session, reserveCommand);
                return null;
            });
        }
        meterRegistry.counter(MetricNames.ORDERS_CONFIRMED).increment();
        LOG.infof("Order confirmed orderId=%s userId=%s from=%s to=%s",
                order.getId(), order.getUserID(), previousStatus, order.getStatus());

        // Best-effort wake-up; if it is lost the scheduled tick still drains the entry.
        outboxRelay.requestPoll();
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderRepository.findById(new org.bson.types.ObjectId(orderId)));
    }

    /** Recorded on orders the buyer stopped themselves, to tell them from the system's own. */
    static final String REASON_BUYER_CANCELLED = "BUYER_CANCELLED";

    /**
     * Stops an order the buyer no longer wants, giving back anything held for it.
     *
     * <p>Cancelling is a lifecycle move rather than an edit, so it stays available after the order
     * has been confirmed, when the order itself can no longer be changed. It stops being available
     * once the charge is in flight - see below.
     *
     * <p>This does three things the status write alone did not, each of which was its own defect:
     * it releases stock the order may be holding, it announces the cancellation like every other
     * path that reaches {@code CANCELLED}, and it writes conditionally on the version so a saga
     * reply cannot overwrite it.
     */
    public Order cancelOrder(String orderId) {
        Order order = orderRepository.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        stateMachine.assertCanTransition(order.getStatus(), OrderStatus.CANCELLED);

        // Legal for the saga, refused for the buyer. RESERVED is not a resting place: the capture
        // was commanded the moment stock came back, so a cancellation here always races a charge
        // already in flight. Cancelling would release the stock and leave the capture to land
        // against an order that no longer exists to pay for - and the platform has no refund path
        // to undo that. The saga cancels from RESERVED too, on a declined charge, but that one
        // knows the money was not taken.
        if (order.getStatus() == OrderStatus.RESERVED) {
            throw new IllegalOrderTransitionException(OrderStatus.RESERVED, OrderStatus.CANCELLED);
        }

        OrderStatus previousStatus = order.getStatus();
        String stepId = order.getSagaStepId();

        order.setStatus(OrderStatus.CANCELLED);
        order.setStatusReason(REASON_BUYER_CANCELLED);
        // Cleared so the deadline sweep stops seeing this order. Left set, it finds one it cannot
        // cancel again, takes the branch written for shipped orders, and clears the deadline while
        // releasing nothing - which is where the leak became permanent and looked like housekeeping.
        order.setSagaStepId(null);
        order.setStepDeadline(null);
        // The capture is never going to happen, so the token has nothing left to be held for.
        order.setPaymentMethodRef(null);

        java.util.List<OutboxEntry> entries = new java.util.ArrayList<>();
        if (stepId != null) {
            // A step was outstanding, so the reserve command is out and may already have been acted
            // on. products-service releases by order id and gives back only what it still holds, so
            // a release for a reservation that never happened is a no-op - while not sending one
            // when it did leaks stock that nothing else will ever free.
            entries.add(outboxEventFactory.releaseStock(orderId, stepId));
        }
        entries.add(outboxEventFactory.orderCancelled(order, REASON_BUYER_CANCELLED));

        // Conditional, and in one transaction with the entries above. persistOrUpdate replaced the
        // document and left the version untouched, so a saga reply that had read the same version
        // still matched and overwrote the cancellation - charging a buyer who had cancelled.
        if (!sagaService.commitOrder(order, entries.toArray(new OutboxEntry[0]))) {
            throw new IllegalOrderTransitionException(previousStatus, OrderStatus.CANCELLED);
        }

        meterRegistry.counter(MetricNames.ORDERS_CANCELLED).increment();
        LOG.infof("Order cancelled by the buyer orderId=%s userId=%s from=%s to=%s released=%s",
                order.getId(), order.getUserID(), previousStatus, order.getStatus(), stepId != null);
        return order;
    }

    /**
     * Records that a paid order has been dispatched.
     *
     * <p>An operator action rather than the buyer's, so unlike {@link #cancelOrder} nothing here
     * looks at who owns the order - the caller's role is the whole permission.
     */
    public Order shipOrder(String orderId) {
        return fulfil(orderId, OrderStatus.SHIPPED, MetricNames.ORDERS_SHIPPED, "shipped");
    }

    /** Records that a shipped order has reached the buyer. Its final state. */
    public Order deliverOrder(String orderId) {
        return fulfil(orderId, OrderStatus.DELIVERED, MetricNames.ORDERS_DELIVERED, "delivered");
    }

    /**
     * Moves an order along the fulfilment tail, or refuses.
     *
     * <p>Written through the saga's conditional write rather than {@code persistOrUpdate}: two
     * operators clicking at once, or one tool retrying, must not both transition. The version guard
     * decides, and the loser is told the order was not in a state it could leave - which by then is
     * true, because the winner already moved it.
     *
     * <p>No event is published. Nothing consumes one: revenue is counted at {@code PAID}, and the
     * catalog already treats paid, shipped and delivered alike when deciding who may review a
     * product. See docs/specs/order-fulfilment.md section 6.
     */
    private Order fulfil(String orderId, OrderStatus target, String metric, String verb) {
        Order order = orderRepository.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        OrderStatus previousStatus = order.getStatus();
        stateMachine.assertCanTransition(previousStatus, target);

        order.setStatus(target);
        if (!sagaService.commitOrder(order)) {
            // Lost the conditional write, so something else moved this order first. The transition
            // asked for is no longer legal from wherever it now is, and saying so is the honest
            // answer: reporting success would claim a dispatch this call did not make.
            throw new IllegalOrderTransitionException(previousStatus, target);
        }

        meterRegistry.counter(metric).increment();
        LOG.infof("Order %s orderId=%s userId=%s from=%s to=%s",
                verb, order.getId(), order.getUserID(), previousStatus, order.getStatus());
        return order;
    }

    /**
     * Rejects the confirmation if the catalog no longer agrees with the prices the order was
     * quoted at. An order can sit unconfirmed indefinitely, so the quote is re-checked rather
     * than trusted.
     *
     * @throws the.chak.ecommerce.orders.control.exceptions.OrderPriceChangedException
     *         if any line's price or discount has moved
     */
    void assertPricesUnchanged(Order order) {
        if (order.getProducts() == null) {
            return;
        }
        List<String> changes = new java.util.ArrayList<>();
        for (the.chak.ecommerce.orders.entity.ProductVO line : order.getProducts()) {
            ProductDto current = productsApiClient.getProduct(line.getProductID());
            if (current == null) {
                throw new ProductNotFoundException(line.getProductID());
            }
            BigDecimal quotedPrice = line.getPrice();
            BigDecimal currentPrice = current.getPrice();
            if (!samePrice(quotedPrice, currentPrice)) {
                changes.add(String.format(Locale.US, "%s was %.2f, now %.2f",
                        line.getTitle(), orZeroAmount(quotedPrice), orZeroAmount(currentPrice)));
                continue;
            }
            // A discount moving changes what is owed just as surely as the list price moving.
            Double quotedDiscount = line.getPercentageOff();
            Double currentDiscount = effectiveDiscount(current);
            if (!sameAmount(quotedDiscount, currentDiscount)) {
                changes.add(String.format(Locale.US, "%s was %.2f at %.0f%% off, now %.0f%% off",
                        line.getTitle(), orZeroAmount(quotedPrice), orZero(quotedDiscount),
                        orZero(currentDiscount)));
            }
        }
        if (!changes.isEmpty()) {
            LOG.infof("Confirmation refused, prices moved orderId=%s changes=%d",
                    order.getId(), changes.size());
            throw new OrderPriceChangedException(changes);
        }
    }

    /** Treats null as absent-and-therefore-zero, so a missing discount equals no discount. */
    private static boolean sameAmount(Double left, Double right) {
        return Double.compare(orZero(left), orZero(right)) == 0;
    }

    /**
     * Compares two amounts by value with null read as zero. Deliberately not
     * {@link BigDecimal#equals}, which also compares scale and would call 10.5 and 10.50 different
     * prices - they are the same amount written two ways, and the catalog may hand back either.
     */
    private static boolean samePrice(BigDecimal left, BigDecimal right) {
        return orZeroAmount(left).compareTo(orZeroAmount(right)) == 0;
    }

    private static BigDecimal orZeroAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0d : value;
    }

    /**
     * Rejects a change to an order that has moved past INITIATED.
     *
     * @throws the.chak.ecommerce.orders.control.exceptions.OrderNotMutableException
     *         if the order can no longer be changed
     */
    public void assertMutable(Order order) {
        if (!stateMachine.isMutable(order.getStatus())) {
            throw new OrderNotMutableException(order.getStatus());
        }
    }

    public void updateOrder(Order order) {
        orderRepository.persistOrUpdate(order);
    }

    public void deleteOrder(Order order) {
        orderRepository.delete(order);
        LOG.infof("Order deleted orderId=%s userId=%s", order.getId(), order.getUserID());
    }
}
