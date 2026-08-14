package the.chak.ecommerce.payment.control.events;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asks for an order to be charged.
 *
 * <p>The one message in the system that carries a payment credential. It is not card data - the
 * reference is opaque and single-use - but it is still the thing a charge can be made against, so
 * this payload is never logged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CapturePaymentCommand {
    private String orderId;
    private String stepId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
}
