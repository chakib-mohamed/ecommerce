package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compensation: gives back whatever the order is holding. Unanswered by design. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseStockCommand {
    private String orderId;
    private String stepId;
}
