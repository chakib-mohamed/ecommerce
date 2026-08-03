package the.chak.ecommerce.orders.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One product and how many of it to hold. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockLine {
    private String productId;
    private Integer quantity;
}
