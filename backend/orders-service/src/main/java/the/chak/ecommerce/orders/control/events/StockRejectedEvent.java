package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Reply: the catalog cannot meet the order and is holding nothing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockRejectedEvent {
    private String orderId;
    private String stepId;
    private String reason;
}
