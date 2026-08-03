package the.chak.ecommerce.products.control.events;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asks the catalog to hold stock for an order.
 *
 * <p>Carries {@code stepId} as well as {@code orderId}: the pair identifies one attempt of one saga
 * step, so a reply can be matched to the step that asked for it and a reply to a step the order has
 * already moved past can be discarded.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockCommand {
    private String orderId;
    private String stepId;
    private List<ReserveStockLine> lines;
}
