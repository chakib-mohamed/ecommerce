package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The charge was refused, with the provider's reason where it gave one. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String orderId;
    private String stepId;
    private String reason;
}
