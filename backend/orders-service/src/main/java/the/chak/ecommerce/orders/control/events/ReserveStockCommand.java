package the.chak.ecommerce.orders.control.events;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Asks the catalog to hold stock for an order. Answered by stock-reserved or stock-rejected. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockCommand {
    private String orderId;
    private String stepId;
    private List<ReserveStockLine> lines;
}
