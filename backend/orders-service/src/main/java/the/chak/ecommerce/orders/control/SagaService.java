package the.chak.ecommerce.orders.control;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;
import the.chak.ecommerce.orders.repository.OutboxRepository;

/**
 * Drives the order saga from the replies its steps produce.
 *
 * <p>Replies are delivered at-least-once and can arrive late, so every one is checked twice before
 * it is acted on: it must name the step the order is currently waiting for, and the move it implies
 * must be legal from where the order actually is. A reply failing either check is a duplicate or a
 * straggler and is dropped, not applied - the state machine is what makes that judgement, so there
 * is no separate dedup table to keep.
 *
 * <p>The status change and whatever it emits are written in one Mongo transaction, on a version
 * condition, for the same reason {@code confirmOrder} does it: two replies racing must not both win.
 */
@ApplicationScoped
public class SagaService {

    private static final Logger LOG = Logger.getLogger(SagaService.class);

    private static final String VERSION_FIELD = "version";

    /** Recorded on the cancellation so the reason survives past the log line. */
    static final String REASON_OUT_OF_STOCK = "OUT_OF_STOCK";

    @Inject
    OrderRepository orderRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    OutboxRelay outboxRelay;

    @Inject
    MongoClient mongoClient;

    @Inject
    OrderStateMachine stateMachine;

    @Inject
    MeterRegistry meterRegistry;

    /** The catalog is holding the order's lines: the order moves on to RESERVED. */
    public void onStockReserved(String orderId, String stepId) {
        Order order = currentStep(orderId, stepId, OrderStatus.RESERVED);
        if (order == null) {
            return;
        }
        order.setStatus(OrderStatus.RESERVED);
        // The step is answered. Payment is the next step and does not exist yet, so nothing is
        // outstanding and there is no deadline to keep - see phase 8.
        order.setSagaStepId(null);
        order.setStepDeadline(null);

        if (commit(order)) {
            meterRegistry.counter(MetricNames.ORDERS_RESERVED).increment();
            LOG.infof("Stock reserved, order advanced orderId=%s", orderId);
        }
    }

    /**
     * The catalog cannot meet the order: it is cancelled.
     *
     * <p>No compensation is issued. The reservation is all-or-nothing, so a refusal means nothing
     * was taken and there is nothing to give back.
     */
    public void onStockRejected(String orderId, String stepId, String reason) {
        Order order = currentStep(orderId, stepId, OrderStatus.CANCELLED);
        if (order == null) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStepId(null);
        order.setStepDeadline(null);

        OutboxEntry cancelled = outboxEventFactory.orderCancelled(order,
                reason == null ? REASON_OUT_OF_STOCK : reason);
        if (commit(order, cancelled)) {
            meterRegistry.counter(MetricNames.ORDERS_CANCELLED).increment();
            LOG.infof("Stock rejected, order cancelled orderId=%s reason=%s", orderId, reason);
        }
    }

    /**
     * Loads the order and decides whether this reply is worth acting on.
     *
     * @return the order, or null when the reply should be dropped
     */
    private Order currentStep(String orderId, String stepId, OrderStatus target) {
        Order order = orderRepository.findById(new ObjectId(orderId));
        if (order == null) {
            LOG.warnf("Reply for unknown order orderId=%s - discarding", orderId);
            return null;
        }
        if (order.getSagaStepId() == null || !order.getSagaStepId().equals(stepId)) {
            // Answers the step that was outstanding when this was sent, not the one outstanding
            // now. Either a redelivery of a reply already applied, or a straggler from a step the
            // deadline sweep has since given up on.
            LOG.infof("Reply for a step that is no longer outstanding orderId=%s stepId=%s "
                    + "current=%s - discarding", orderId, stepId, order.getSagaStepId());
            return null;
        }
        if (!stateMachine.canTransition(order.getStatus(), target)) {
            LOG.infof("Reply implies an illegal move orderId=%s from=%s to=%s - discarding",
                    orderId, order.getStatus(), target);
            return null;
        }
        return order;
    }

    /**
     * Writes the order and any event it produced, conditional on the version read.
     *
     * @return true when this write won
     */
    private boolean commit(Order order, OutboxEntry... entries) {
        Long readVersion = order.getVersion();
        order.setVersion(readVersion == null ? 1L : readVersion + 1);
        Bson expectedVersion = Filters.eq(VERSION_FIELD, readVersion);

        Order toWrite = order;
        boolean[] won = { false };
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                UpdateResult result = orderRepository.mongoCollection().replaceOne(
                        session,
                        Filters.and(Filters.eq("_id", toWrite.id), expectedVersion),
                        toWrite);
                if (result.getMatchedCount() == 0) {
                    // Something else moved the order between the read and here. Losing quietly is
                    // right: this reply is stale, and the write that won has already acted.
                    return null;
                }
                for (OutboxEntry entry : entries) {
                    outboxRepository.mongoCollection().insertOne(session, entry);
                }
                won[0] = true;
                return null;
            });
        }
        if (!won[0]) {
            LOG.infof("Saga write lost a race orderId=%s - discarding the reply", toWrite.id);
            return false;
        }
        outboxRelay.requestPoll();
        return true;
    }

    /**
     * Exposed for the deadline sweep, which needs the same conditional write. Every entry lands in
     * the one transaction as the status change, so a compensation can never be published for a
     * cancellation that did not commit - nor lost after one that did.
     */
    boolean commitOrder(Order order, OutboxEntry... entries) {
        return commit(order, entries);
    }
}
