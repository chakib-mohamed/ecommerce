package the.chak.ecommerce.orders.control;

import java.time.Instant;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.entity.OutboxEntry;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * Gives up on saga steps that were never answered.
 *
 * <p>This is not a tidying job, it is the only thing that ever frees stock held by a stalled saga.
 * Reservations have no expiry by decision (section 10.3 of the lifecycle spec), and the outbox relay
 * abandons a record after its retry cap with nothing but a log line - so a command that dies there
 * would otherwise leave an order waiting forever with stock held out of sale. Without this sweep,
 * one stuck order removes inventory permanently and silently.
 *
 * <p>A timed-out step is treated as failed: the order is cancelled and its compensation issued. The
 * compensation is idempotent, so issuing one for stock that was never held costs nothing - and that
 * matters, because a step can time out precisely when nobody knows whether the work happened.
 */
@ApplicationScoped
public class SagaDeadlineSweep {

    private static final Logger LOG = Logger.getLogger(SagaDeadlineSweep.class);

    /** Recorded on orders the sweep gives up on, to tell them from a buyer's own cancellation. */
    static final String REASON_STEP_TIMEOUT = "SAGA_STEP_TIMEOUT";

    /** Bounded so one sweep cannot monopolise the scheduler if a great many steps expire at once. */
    private static final int BATCH_SIZE = 100;

    @Inject
    OrderRepository orderRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    SagaService sagaService;

    @Inject
    OrderStateMachine stateMachine;

    @Inject
    MeterRegistry meterRegistry;

    @Scheduled(every = "{orders.saga.sweep-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        List<Order> expired = orderRepository.findExpiredSteps(Instant.now(), BATCH_SIZE);
        if (expired.isEmpty()) {
            return;
        }
        LOG.warnf("Sweeping %d saga step(s) past their deadline", expired.size());
        expired.forEach(this::abandon);
    }

    private void abandon(Order order) {
        String orderId = order.id.toString();
        String stepId = order.getSagaStepId();

        if (!stateMachine.canTransition(order.getStatus(), OrderStatus.CANCELLED)) {
            // Past the point where cancelling is legal - shipped, say. Clearing the deadline stops
            // the sweep picking it up forever; a step this old is not coming back.
            LOG.warnf("Expired step on an order that can no longer be cancelled orderId=%s "
                    + "status=%s - clearing the deadline only", orderId, order.getStatus());
            order.setSagaStepId(null);
            order.setStepDeadline(null);
            sagaService.commitOrder(order);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStepId(null);
        order.setStepDeadline(null);

        // Compensate before announcing: the release is what actually returns the stock, and it is
        // idempotent, so issuing one for a step that never took anything is harmless.
        OutboxEntry release = outboxEventFactory.releaseStock(orderId, stepId);
        OutboxEntry cancelled = outboxEventFactory.orderCancelled(order, REASON_STEP_TIMEOUT);

        // Both entries go in with the status change. Written separately, a crash between them
        // could release the stock without ever announcing the cancellation, or the reverse.
        if (sagaService.commitOrder(order, release, cancelled)) {
            meterRegistry.counter(MetricNames.SAGAS_TIMED_OUT).increment();
            LOG.warnf("Saga step timed out, order cancelled and stock released orderId=%s stepId=%s",
                    orderId, stepId);
        }
    }
}
