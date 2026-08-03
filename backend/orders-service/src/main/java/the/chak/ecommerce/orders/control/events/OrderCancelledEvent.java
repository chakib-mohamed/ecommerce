package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The order will not be fulfilled. Emitted on every route to CANCELLED so downstream readers - the
 * warehouse above all - converge instead of holding an order that no longer exists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private String reason;
}
