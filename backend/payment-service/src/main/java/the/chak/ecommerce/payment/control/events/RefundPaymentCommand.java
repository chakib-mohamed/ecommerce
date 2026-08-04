package the.chak.ecommerce.payment.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Asks for a charge already made to be returned. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentCommand {
    private String orderId;
    private String stepId;
}
