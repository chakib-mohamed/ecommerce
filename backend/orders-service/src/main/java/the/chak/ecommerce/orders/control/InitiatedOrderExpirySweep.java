package the.chak.ecommerce.orders.control;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * Closes orders the buyer priced and then walked away from.
 *
 * <p>An {@code INITIATED} order is a quote: checked out from the cart, priced, not committed. It
 * holds no stock and has taken no money, so unlike the saga deadline sweep this one compensates
 * nothing - there is nothing to give back. What it does is stop the collection growing without
 * bound and stop a buyer's history filling with orders they never placed.
 *
 * <p><b>This reverses section 10.2 of the lifecycle spec</b>, which decided against an expiry on the
 * grounds that a stale quote is harmless because price is revalidated at confirm. That reasoning is
 * still correct - nothing here is about billing safety, and an expired order could not have been
 * billed wrongly anyway. It is about the orders never going away: nothing in the UI can confirm an
 * order once the checkout flow that created it is gone, so an abandoned one is unreachable rather
 * than merely old.
 */
@ApplicationScoped
public class InitiatedOrderExpirySweep {

    private static final Logger LOG = Logger.getLogger(InitiatedOrderExpirySweep.class);

    /** Recorded on expired orders, to tell them from a buyer's own cancellation. */
    static final String REASON_EXPIRED = "INITIATED_EXPIRED";

    /** Bounded so one sweep cannot monopolise the scheduler if a great many expire at once. */
    private static final int BATCH_SIZE = 100;

    @Inject
    OrderRepository orderRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    SagaService sagaService;

    @Inject
    MeterRegistry meterRegistry;

    /**
     * How long a priced-but-uncommitted order is kept before it is cancelled.
     *
     * <p>A day, because that is roughly how long an abandoned checkout stays plausibly live: long
     * enough that a buyer who wandered off mid-purchase and came back the same evening is not
     * surprised, short enough that the collection does not accumulate quotes nobody can act on.
     * Configurable, because the right answer is a business call rather than a technical one.
     */
    @ConfigProperty(name = "orders.initiated.ttl", defaultValue = "PT24H")
    Duration ttl;

    @Scheduled(every = "{orders.initiated.sweep-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ttl);
        List<Order> expired = orderRepository.findExpiredInitiated(cutoff, BATCH_SIZE);
        if (expired.isEmpty()) {
            return;
        }
        LOG.infof("Expiring %d order(s) left uncommitted for longer than %s", expired.size(), ttl);
        expired.forEach(this::expire);
    }

    private void expire(Order order) {
        String orderId = order.id.toString();

        // Guarded even though the query already filtered on INITIATED: the read and the write are
        // not atomic, so a buyer can confirm in between.
        //
        // The question is "is this still a quote", not "may it be cancelled" - and the difference
        // is not academic. RESERVED -> CANCELLED is a legal transition, so asking the state machine
        // would let this sweep cancel an order that had already reserved stock, while issuing no
        // release for it, because this sweep deliberately compensates nothing. That would leak
        // inventory permanently: precisely the failure the saga sweep exists to prevent, caused by
        // the job meant to be harmless.
        if (order.getStatus() != OrderStatus.INITIATED) {
            LOG.infof("Order moved on before it could be expired orderId=%s status=%s - leaving it "
                    + "to the saga, which knows what it is holding", orderId, order.getStatus());
            return;
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setStatusReason(REASON_EXPIRED);

        // No compensation: an INITIATED order holds no stock and has taken no money. That is the
        // whole difference between this sweep and the saga one, which must release what it gave up
        // on. Announced all the same, so anything reading the cancellation stream sees every
        // cancellation rather than only some of them.
        OutboxEntry cancelled = outboxEventFactory.orderCancelled(order, REASON_EXPIRED);

        if (sagaService.commitOrder(order, cancelled)) {
            meterRegistry.counter(MetricNames.ORDERS_EXPIRED).increment();
            LOG.infof("Order expired unconfirmed orderId=%s userId=%s from=%s to=%s age>%s",
                    orderId, order.getUserID(), previousStatus, order.getStatus(), ttl);
        }
    }
}
