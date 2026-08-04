package the.chak.ecommerce.payment.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import the.chak.ecommerce.payment.control.events.CapturePaymentCommand;
import the.chak.ecommerce.payment.control.events.RefundPaymentCommand;

/**
 * Kafka entry point for the saga's payment commands.
 *
 * <p>Logs the order and step but never the command itself: the capture carries the buyer's payment
 * method reference, and a log line is exactly the sort of place a credential should not end up.
 */
@ApplicationScoped
public class PaymentCommandConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentCommandConsumer.class);

    @Inject
    PaymentService paymentService;

    @Incoming("capture-payment")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeCapture(CapturePaymentCommand command) {
        LOG.infof("Capture-payment command received orderId=%s stepId=%s",
                command.getOrderId(), command.getStepId());
        paymentService.capture(command);
    }

    @Incoming("refund-payment")
    @Retry(maxRetries = 3, delay = 200)
    public void consumeRefund(RefundPaymentCommand command) {
        LOG.infof("Refund-payment command received orderId=%s stepId=%s",
                command.getOrderId(), command.getStepId());
        paymentService.refund(command);
    }
}
