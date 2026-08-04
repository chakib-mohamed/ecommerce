package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The money was taken. Carries the provider's reference so a refund has something to go by. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCapturedEvent {
    private String orderId;
    private String stepId;
    private String providerRef;
}
