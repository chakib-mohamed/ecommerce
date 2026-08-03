package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reply: the catalog could not meet the order and is holding nothing.
 *
 * <p>Nothing needs releasing after this - the reservation is all-or-nothing, so a refusal leaves
 * stock exactly as it was.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockRejectedEvent {
    private String orderId;
    private String stepId;
    private String reason;
}
