package the.chak.ecommerce.payment.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The charge was refused.
 *
 * <p>A refusal is an answer and must be sent. Without it the order would stall to its step deadline
 * and be cancelled for a timeout, which tells the buyer nothing about why.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String orderId;
    private String stepId;
    private String reason;
}
