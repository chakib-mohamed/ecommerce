package the.chak.ecommerce.payment.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import the.chak.ecommerce.payment.control.events.CapturePaymentCommand;
import the.chak.ecommerce.payment.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.payment.control.events.PaymentFailedEvent;
import the.chak.ecommerce.payment.entity.OutboxEvent;
import the.chak.ecommerce.payment.entity.Payment;
import the.chak.ecommerce.payment.entity.PaymentStatus;
import the.chak.ecommerce.payment.repository.OutboxRepository;
import the.chak.ecommerce.payment.repository.PaymentRepository;

/**
 * Charging the buyer, and saying what happened.
 *
 * <p>Two things make this different from the other saga participants. The work is not undoable - a
 * transaction that rolls back cannot un-charge a card - and the work happens at a third party that
 * can stop answering mid-call. So the cases that matter are the ones where the answer is bad or
 * missing: a decline must be reported rather than thrown away, a redelivery must not charge twice,
 * and a provider that does not answer must leave nothing recorded so the command redelivers.
 */
class PaymentServiceTest {

    private static final String ORDER_ID = "6512c0ffee00000000000001";
    private static final String STEP_ID = "step-1";
    private static final String PROVIDER_REF = "pi_123";
    private static final String PAYMENT_METHOD = "pm_card_visa";
    private static final BigDecimal AMOUNT = new BigDecimal("49.99");

    /** A boundary that just runs the work: no container here, and none of these tests need one. */
    private static final TransactionBoundary INLINE_TRANSACTION = new TransactionBoundary() {
        @Override
        public <T> T call(java.util.function.Supplier<T> work) {
            return work.get();
        }

        @Override
        public void run(Runnable work) {
            work.run();
        }
    };

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
    private final StripeGatewayClient gateway = mock(StripeGatewayClient.class);
    private final OutboxRelay outboxRelay = mock(OutboxRelay.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    private PaymentService service() {
        when(outboxEventFactory.paymentCaptured(any(), any())).thenReturn(new OutboxEvent());
        when(outboxEventFactory.paymentFailed(any(), any())).thenReturn(new OutboxEvent());
        // No default for findAttempt: Mockito already answers Optional.empty(), and stubbing it
        // here would override the per-test stubs, which are set before this helper runs.

        PaymentService service = new PaymentService();
        service.paymentRepository = paymentRepository;
        service.outboxRepository = outboxRepository;
        service.outboxEventFactory = outboxEventFactory;
        service.gateway = gateway;
        service.outboxRelay = outboxRelay;
        // Runs the work inline. There is no transaction manager here, and these tests are about
        // what capture does, not where its transactions begin - that boundary is asserted by
        // TransactionBoundaryTest and by the end-to-end suite, which is what caught its absence.
        service.transaction = INLINE_TRANSACTION;
        service.meterRegistry = meterRegistry;
        return service;
    }

    // -- the charge succeeds -------------------------------------------------

    @Test
    @DisplayName("Charges the buyer for what the order is worth")
    void capture_chargesTheOrderAmount() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then
        verify(gateway).charge(any(), eq(AMOUNT), eq("EUR"), eq(PAYMENT_METHOD));
    }

    @Test
    @DisplayName("Charges under the saga step's id so a repeat cannot take the money twice")
    void capture_usesTheStepAsTheIdempotencyKey() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then - this, not the local record, is what actually protects the buyer: it holds even if
        // this service loses its database entirely
        verify(gateway).charge(eq(STEP_ID), any(), any(), any());
    }

    @Test
    @DisplayName("Records the charge with the provider's reference")
    void capture_recordsTheCharge() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then - without the reference there is no way to refund the charge or reconcile it
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).persist(saved.capture());
        assertEquals(PaymentStatus.CAPTURED, saved.getValue().getStatus());
        assertEquals(PROVIDER_REF, saved.getValue().getProviderRef());
    }

    @Test
    @DisplayName("Replies that the payment was captured")
    void capture_repliesCaptured() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then
        ArgumentCaptor<PaymentCapturedEvent> reply =
                ArgumentCaptor.forClass(PaymentCapturedEvent.class);
        verify(outboxEventFactory).paymentCaptured(eq(ORDER_ID), reply.capture());
        verify(outboxRepository).persist(any(OutboxEvent.class));
        assertEquals(STEP_ID, reply.getValue().getStepId(),
                "the reply must carry back the step it answers");
    }

    @Test
    @DisplayName("Never keeps the payment method once the charge is made")
    void capture_doesNotStoreThePaymentMethod() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then - the provider's own reference is what a refund needs; keeping the credential too
        // would be holding a way to charge the buyer again, for no reason
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).persist(saved.capture());
        assertFalseContains(saved.getValue(), PAYMENT_METHOD);
    }

    // -- the charge is declined ----------------------------------------------

    @Test
    @DisplayName("Replies that the payment failed when the provider refuses")
    void capture_declined_repliesFailed() {
        // given
        when(gateway.charge(any(), any(), any(), any()))
                .thenReturn(ChargeResult.declined("card_declined"));

        // when
        service().capture(captureCommand());

        // then - a refusal is an answer; without it the order stalls to its deadline and is
        // cancelled for a timeout, which tells the buyer nothing
        ArgumentCaptor<PaymentFailedEvent> reply = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(outboxEventFactory).paymentFailed(eq(ORDER_ID), reply.capture());
        assertEquals("card_declined", reply.getValue().getReason());
        verify(outboxEventFactory, never()).paymentCaptured(any(), any());
    }

    @Test
    @DisplayName("Records the refusal and why")
    void capture_declined_recordsTheFailure() {
        // given
        when(gateway.charge(any(), any(), any(), any()))
                .thenReturn(ChargeResult.declined("insufficient_funds"));

        // when
        service().capture(captureCommand());

        // then
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).persist(saved.capture());
        assertEquals(PaymentStatus.FAILED, saved.getValue().getStatus());
        assertEquals("insufficient_funds", saved.getValue().getFailureReason());
    }

    // -- the provider does not answer ----------------------------------------

    @Test
    @DisplayName("Records nothing when the provider does not answer")
    void capture_gatewayFault_recordsNothing() {
        // given - nobody knows whether money moved
        when(gateway.charge(any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("timeout"));

        // when
        assertThrows(PaymentGatewayException.class, () -> service().capture(captureCommand()));

        // then - recording a failure here would be a lie the order acts on: it would cancel and
        // release stock for a charge that may well have gone through
        verify(paymentRepository, never()).persist(any(Payment.class));
        verify(outboxRepository, never()).persist(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Lets the command redeliver when the provider does not answer")
    void capture_gatewayFault_propagates() {
        // given
        when(gateway.charge(any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("timeout"));

        // when / then - swallowed, the command would be acknowledged and the order would wait on a
        // reply that is never coming
        assertThrows(PaymentGatewayException.class, () -> service().capture(captureCommand()));
    }

    // -- the command arrives twice -------------------------------------------

    @Test
    @DisplayName("Does not charge again for an attempt already made")
    void capture_redelivered_doesNotChargeTwice() {
        // given
        when(paymentRepository.findAttempt(ORDER_ID, STEP_ID))
                .thenReturn(Optional.of(capturedPayment()));

        // when
        service().capture(captureCommand());

        // then
        verify(gateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Replays the original outcome when a command is redelivered")
    void capture_redelivered_replaysTheOutcome() {
        // given - the reply, not the charge, is what was lost; staying silent would leave the order
        // waiting on an answer that already happened
        when(paymentRepository.findAttempt(ORDER_ID, STEP_ID))
                .thenReturn(Optional.of(capturedPayment()));

        // when
        service().capture(captureCommand());

        // then
        ArgumentCaptor<PaymentCapturedEvent> reply =
                ArgumentCaptor.forClass(PaymentCapturedEvent.class);
        verify(outboxEventFactory).paymentCaptured(eq(ORDER_ID), reply.capture());
        assertEquals(PROVIDER_REF, reply.getValue().getProviderRef());
    }

    @Test
    @DisplayName("Replays a refusal too, rather than trying the card again")
    void capture_redeliveredAfterFailure_replaysTheRefusal() {
        // given - retrying could succeed where the first attempt failed, so the order would be told
        // both that it failed and that it was paid
        Payment failed = capturedPayment();
        failed.setStatus(PaymentStatus.FAILED);
        failed.setProviderRef(null);
        failed.setFailureReason("card_declined");
        when(paymentRepository.findAttempt(ORDER_ID, STEP_ID)).thenReturn(Optional.of(failed));

        // when
        service().capture(captureCommand());

        // then
        verify(gateway, never()).charge(any(), any(), any(), any());
        verify(outboxEventFactory).paymentFailed(eq(ORDER_ID), any(PaymentFailedEvent.class));
    }

    // -- what an operator can see -------------------------------------------

    @Test
    @DisplayName("Counts a charge the provider accepted")
    void capture_captured_isCounted() {
        // given
        when(gateway.charge(any(), any(), any(), any())).thenReturn(ChargeResult.captured(PROVIDER_REF));

        // when
        service().capture(captureCommand());

        // then
        assertEquals(1.0, meterRegistry.get(MetricNames.PAYMENTS_CAPTURED).counter().count(), 0.001);
    }

    @Test
    @DisplayName("Counts a refusal separately from a fault")
    void capture_declined_isCountedAsADecline() {
        // given - a decline is a normal answer; conflating it with a fault would make an ordinary
        // Tuesday look like an outage
        when(gateway.charge(any(), any(), any(), any()))
                .thenReturn(ChargeResult.declined("card_declined"));

        // when
        service().capture(captureCommand());

        // then
        assertEquals(1.0, meterRegistry.get(MetricNames.PAYMENTS_DECLINED).counter().count(), 0.001);
        assertNull(meterRegistry.find(MetricNames.PAYMENTS_GATEWAY_FAULTS).counter());
    }

    @Test
    @DisplayName("Counts a provider that did not answer, because nobody knows if money moved")
    void capture_gatewayFault_isCounted() {
        // given - the worst case in the spec: the charge may have gone through, and the saga will
        // cancel the order anyway. A log line alone is not something anyone is watching
        when(gateway.charge(any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("timeout"));

        // when
        assertThrows(PaymentGatewayException.class, () -> service().capture(captureCommand()));

        // then
        assertEquals(1.0,
                meterRegistry.get(MetricNames.PAYMENTS_GATEWAY_FAULTS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("Still lets the command redeliver after counting a fault")
    void capture_gatewayFault_stillPropagates() {
        // given - counting must not swallow it; the rethrow is what makes the retry happen
        when(gateway.charge(any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("timeout"));

        // when / then
        assertThrows(PaymentGatewayException.class, () -> service().capture(captureCommand()));
        verify(paymentRepository, never()).persist(any(Payment.class));
    }

    @Test
    @DisplayName("Counts a redelivered capture without counting a second charge")
    void capture_redelivered_isCountedAsARedelivery() {
        // given - counting it as a capture would inflate the charge rate with messages that
        // charged nothing
        when(paymentRepository.findAttempt(ORDER_ID, STEP_ID))
                .thenReturn(Optional.of(capturedPayment()));

        // when
        service().capture(captureCommand());

        // then
        assertEquals(1.0,
                meterRegistry.get(MetricNames.PAYMENTS_REDELIVERED).counter().count(), 0.001);
        assertNull(meterRegistry.find(MetricNames.PAYMENTS_CAPTURED).counter());
    }

    private static CapturePaymentCommand captureCommand() {
        return new CapturePaymentCommand(ORDER_ID, STEP_ID, AMOUNT, "EUR", PAYMENT_METHOD);
    }

    private static Payment capturedPayment() {
        Payment payment = new Payment();
        payment.setOrderId(ORDER_ID);
        payment.setStepId(STEP_ID);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderRef(PROVIDER_REF);
        payment.setAmount(AMOUNT);
        payment.setCurrency("EUR");
        return payment;
    }

    /** No field of the record may hold the payment method, whatever it is called. */
    private static void assertFalseContains(Payment payment, String secret) {
        String flattened = String.join("|", String.valueOf(payment.getOrderId()),
                String.valueOf(payment.getStepId()), String.valueOf(payment.getProviderRef()),
                String.valueOf(payment.getFailureReason()), String.valueOf(payment.getCurrency()));
        assertTrue(!flattened.contains(secret),
                "the payment record must not hold the payment method: " + flattened);
    }
}
