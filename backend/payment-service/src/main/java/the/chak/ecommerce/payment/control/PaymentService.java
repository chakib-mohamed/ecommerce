package the.chak.ecommerce.payment.control;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import the.chak.ecommerce.payment.control.events.CapturePaymentCommand;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;
import the.chak.ecommerce.payment.control.events.RefundPaymentCommand;
import the.chak.ecommerce.payment.entity.Payment;
import the.chak.ecommerce.payment.entity.PaymentStatus;
import the.chak.ecommerce.payment.repository.OutboxRepository;
import the.chak.ecommerce.payment.repository.PaymentRepository;

/**
 * The saga's payment step, seen from this side: charge the buyer, record what happened, and say so.
 *
 * <p>The ordering is the whole design. The provider is called <b>outside</b> any transaction, because
 * network I/O inside one holds a database lock open across a call that can hang - and because a
 * transaction that rolls back cannot un-charge a card. Only the provider's answer crosses into the
 * transaction, where the payment record and the reply are written together.
 *
 * <p>That leaves exactly one unrecoverable case, and it is deliberate rather than overlooked: the
 * provider takes the money and the answer never arrives. Nothing is recorded, the command
 * redelivers, and the idempotency key means the retry returns the original charge instead of making
 * a second one. If the retry never happens the order times out with money taken, which is the case
 * section 8 of the spec says must be alerted on rather than absorbed.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    StripeGatewayClient gateway;

    @Inject
    OutboxRelay outboxRelay;

    @Inject
    TransactionBoundary transaction;

    @Inject
    io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * Charges the order and replies with the outcome.
     *
     * <p>Safe to redeliver: an attempt already recorded is replayed rather than charged again.
     */
    public void capture(CapturePaymentCommand command) {
        // Its own transaction. This runs on a Kafka consumer thread, where nothing is active until
        // something starts it: without this the entity manager cannot be touched at all.
        if (transaction.call(() -> replayIfAlreadyAttempted(command))) {
            outboxRelay.requestPoll();
            return;
        }

        // Outside any transaction, deliberately: this is network I/O, and a transaction that rolled
        // back could not un-charge the card anyway. A fault here propagates with nothing written,
        // so the command redelivers and the idempotency key makes the retry the same charge.
        ChargeResult result;
        try {
            result = gateway.charge(command.getStepId(), command.getAmount(),
                    command.getCurrency(), command.getPaymentMethod());
        } catch (PaymentGatewayException e) {
            // Counted and rethrown, not handled. The rethrow is what leaves the command to
            // redeliver; the count is what makes this visible, because from here nobody knows
            // whether the money moved and the saga will cancel the order regardless.
            meterRegistry.counter(MetricNames.PAYMENTS_GATEWAY_FAULTS).increment();
            throw e;
        }

        ChargeResult outcome = result;
        transaction.run(() -> record(command, outcome));
        // After the commit, not inside it: a relay woken while the row is still uncommitted finds
        // nothing and goes back to sleep. Best-effort either way - the scheduled tick still drains.
        outboxRelay.requestPoll();
    }

    /**
     * Replays the outcome of a capture already attempted, if there is one.
     *
     * @return whether this command was a redelivery and has now been answered
     */
    private boolean replayIfAlreadyAttempted(CapturePaymentCommand command) {
        Optional<Payment> alreadyAttempted =
                paymentRepository.findAttempt(command.getOrderId(), command.getStepId());
        if (alreadyAttempted.isEmpty()) {
            return false;
        }
        // The charge happened; it was the reply that was lost. Replaying it is not optional -
        // staying silent leaves the order waiting on an answer that already exists.
        Payment payment = alreadyAttempted.get();
        LOG.infof("Capture redelivered, replaying the recorded outcome orderId=%s stepId=%s "
                + "status=%s", command.getOrderId(), command.getStepId(), payment.getStatus());
        reply(payment);
        meterRegistry.counter(MetricNames.PAYMENTS_REDELIVERED).increment();
        return true;
    }

    /**
     * Writes the outcome and the reply together.
     *
     * <p>One transaction, for the reason ADR-0002 exists: a charge recorded while its reply was
     * lost would leave an order waiting on money that had already been taken.
     */
    // Not @Transactional: capture() calls this directly, and an interceptor does not run on a call
    // that never leaves the bean. The transaction is opened by the caller, where it is visible.
    void record(CapturePaymentCommand command, ChargeResult result) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(command.getOrderId());
        payment.setStepId(command.getStepId());
        payment.setAmount(command.getAmount());
        payment.setCurrency(command.getCurrency());
        payment.setCreatedAt(Instant.now());
        payment.setStatus(result.captured() ? PaymentStatus.CAPTURED : PaymentStatus.FAILED);
        payment.setProviderRef(result.providerRef());
        payment.setFailureReason(result.failureReason());
        // The payment method is deliberately not copied onto the record. The provider's own
        // reference is what a refund needs; keeping the credential too would be retaining a way to
        // charge the buyer again, for no reason.
        paymentRepository.persist(payment);

        reply(payment);

        meterRegistry.counter(result.captured()
                ? MetricNames.PAYMENTS_CAPTURED : MetricNames.PAYMENTS_DECLINED).increment();

        LOG.infof("Capture handled orderId=%s stepId=%s outcome=%s",
                command.getOrderId(), command.getStepId(), payment.getStatus());
    }

    /** Returns the money for an order already charged. */
    @Transactional
    public void refund(RefundPaymentCommand command) {
        Optional<Payment> captured = paymentRepository.findCapturedFor(command.getOrderId());
        if (captured.isEmpty()) {
            // Normal, not exceptional: compensating a saga that failed before the capture asks for
            // a refund of a charge that was never made.
            LOG.infof("Refund for an order with no charge orderId=%s - nothing to return",
                    command.getOrderId());
            return;
        }
        Payment payment = captured.get();
        gateway.refund(command.getStepId(), payment.getProviderRef());
        payment.setStatus(PaymentStatus.REFUNDED);
        meterRegistry.counter(MetricNames.PAYMENTS_REFUNDED).increment();
        LOG.infof("Refunded orderId=%s providerRef=%s",
                command.getOrderId(), payment.getProviderRef());
    }

    private void reply(Payment payment) {
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            outboxRepository.persist(outboxEventFactory.paymentCaptured(payment.getOrderId(),
                    new PaymentCapturedEvent(payment.getOrderId(), payment.getStepId(),
                            payment.getProviderRef())));
        } else {
            outboxRepository.persist(outboxEventFactory.paymentFailed(payment.getOrderId(),
                    new PaymentFailedEvent(payment.getOrderId(), payment.getStepId(),
                            payment.getFailureReason())));
        }
    }
}
