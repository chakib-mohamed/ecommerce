package the.chak.ecommerce.orders.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.control.events.PaymentCapturedEvent;
import the.chak.ecommerce.orders.control.events.PaymentFailedEvent;

/**
 * Kafka entry point for the payment step's replies. Holds no decisions of its own - whether a reply
 * is still relevant is {@link SagaService}'s judgement, and it needs the order to make it.
 */
@ApplicationScoped
public class PaymentReplyConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentReplyConsumer.class);

    @Inject
    SagaService sagaService;

    @Incoming("payment-captured")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeCaptured(PaymentCapturedEvent event) {
        LOG.infof("Payment-captured reply received orderId=%s stepId=%s",
                event.getOrderId(), event.getStepId());
        sagaService.onPaymentCaptured(event.getOrderId(), event.getStepId(),
                event.getProviderRef());
    }

    @Incoming("payment-failed")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeFailed(PaymentFailedEvent event) {
        LOG.infof("Payment-failed reply received orderId=%s stepId=%s reason=%s",
                event.getOrderId(), event.getStepId(), event.getReason());
        sagaService.onPaymentFailed(event.getOrderId(), event.getStepId(), event.getReason());
    }
}
