package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reply: the catalog is holding every line of the order. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedEvent {
    private String orderId;
    private String stepId;
}
