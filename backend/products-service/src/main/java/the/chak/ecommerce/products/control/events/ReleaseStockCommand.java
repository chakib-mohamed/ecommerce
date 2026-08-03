package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compensation for {@link ReserveStockCommand}: gives back whatever the order is holding.
 *
 * <p>Carries no lines. What was held is already recorded against the order, and re-stating it here
 * would let a malformed compensation give back more than was ever taken.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseStockCommand {
    private String orderId;
    private String stepId;
}
