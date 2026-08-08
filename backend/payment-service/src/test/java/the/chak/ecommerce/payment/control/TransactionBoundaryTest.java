package the.chak.ecommerce.payment.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.payment.control.events.CapturePaymentCommand;
import the.chak.ecommerce.payment.entity.OutboxEvent;
import the.chak.ecommerce.payment.entity.Payment;
import the.chak.ecommerce.payment.entity.PaymentStatus;
import the.chak.ecommerce.payment.repository.OutboxRepository;
import the.chak.ecommerce.payment.repository.PaymentRepository;

/**
 * Where {@link PaymentService#capture} opens a transaction, and where it must not.
 *
 * <p>The database work ran with no transaction at all: {@code capture} was not annotated - it must
 * not be, see below - and its first repository call sat directly in that unannotated method. On a
 * Kafka consumer thread nothing is active until something starts one, so every capture failed with
 * "neither a transaction nor a CDI request context is active", took the channel down, and left
 * every confirmed order to time out. Nothing caught it because these unit tests hold no container
 * and payment-service has no test that exercises capture through a real one.
 *
 * <p>The other half matters just as much and fails far more quietly: the call to the payment
 * provider must stay <em>outside</em> any transaction. It is network I/O with a ten-second
 * timeout, and holding a database connection across it would tie up the pool waiting on someone
 * else's server - for nothing, since a rollback cannot un-charge a card.
 */
class TransactionBoundaryTest {

    private static final String ORDER_ID = "6512c0ffee00000000000001";
    private static final String STEP_ID = "step-1";
    private static final BigDecimal AMOUNT = new BigDecimal("49.99");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final StripeGatewayClient gateway = mock(StripeGatewayClient.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    /** Runs the work, and reports whether a transaction is open while it does. */
    private static final class RecordingBoundary extends TransactionBoundary {
        private int depth;
        private int opened;

        boolean isOpen() {
            return depth > 0;
        }

        int opened() {
            return opened;
        }

        @Override
        public <T> T call(Supplier<T> work) {
            depth++;
            opened++;
            try {
                return work.get();
            } finally {
                depth--;
            }
        }

        @Override
        public void run(Runnable work) {
            depth++;
            opened++;
            try {
                work.run();
            } finally {
                depth--;
            }
        }
    }

    private final RecordingBoundary boundary = new RecordingBoundary();

    private PaymentService service() {
        when(outboxEventFactory.paymentCaptured(any(), any())).thenReturn(new OutboxEvent());
        when(outboxEventFactory.paymentFailed(any(), any())).thenReturn(new OutboxEvent());

        PaymentService service = new PaymentService();
        service.paymentRepository = paymentRepository;
        service.outboxRepository = outboxRepository;
        service.outboxEventFactory = outboxEventFactory;
        service.gateway = gateway;
        service.outboxRelay = outboxRelay;
        service.transaction = boundary;
        service.meterRegistry = meterRegistry;
        return service;
    }

    @Test
    @DisplayName("Does not hold a transaction open while calling the payment provider")
    void charges_outsideAnyTransaction() {
        // given - the gateway reports what was true at the moment it was called
        boolean[] openDuringCharge = {true};
        when(gateway.charge(any(), any(), any(), any())).thenAnswer(invocation -> {
            openDuringCharge[0] = boundary.isOpen();
            return ChargeResult.captured("pi_123");
        });

        // when
        service().capture(command());

        // then
        assertFalse(openDuringCharge[0],
                "the provider was called inside a transaction: a database connection is being "
                        + "held for the length of someone else's network call, and a rollback "
                        + "could not un-charge the card anyway");
    }

    @Test
    @DisplayName("Writes the outcome inside a transaction")
    void records_insideATransaction() {
        // given
        boolean[] openDuringPersist = {false};
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured("pi_123"));
        org.mockito.Mockito.doAnswer(invocation -> {
            openDuringPersist[0] = boundary.isOpen();
            return null;
        }).when(paymentRepository).persist(any(Payment.class));

        // when
        service().capture(command());

        // then - the charge and its reply have to commit together, or an order waits forever on
        // money that was already taken
        assertTrue(openDuringPersist[0],
                "the payment was written with no transaction open - on a consumer thread this "
                        + "does not merely lose atomicity, it throws");
    }

    @Test
    @DisplayName("Reads the earlier attempt inside a transaction")
    void redelivery_replaysInsideATransaction() {
        // given - a capture that already happened, whose reply was lost
        boolean[] openDuringLookup = {false};
        when(paymentRepository.findAttempt(ORDER_ID, STEP_ID)).thenAnswer(invocation -> {
            openDuringLookup[0] = boundary.isOpen();
            return Optional.of(capturedPayment());
        });

        // when
        service().capture(command());

        // then
        assertTrue(openDuringLookup[0],
                "the redelivery check ran with nothing active; this is the exact call that threw "
                        + "in production, on a consumer thread with no context of its own");
        assertEquals(1, boundary.opened(), "a redelivery should open one transaction, not two");
    }

    private static CapturePaymentCommand command() {
        return new CapturePaymentCommand(ORDER_ID, STEP_ID, AMOUNT, "EUR", "pm_card_visa");
    }

    private static Payment capturedPayment() {
        Payment payment = new Payment();
        payment.setOrderId(ORDER_ID);
        payment.setStepId(STEP_ID);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderRef("pi_123");
        payment.setAmount(AMOUNT);
        payment.setCurrency("EUR");
        return payment;
    }
}
