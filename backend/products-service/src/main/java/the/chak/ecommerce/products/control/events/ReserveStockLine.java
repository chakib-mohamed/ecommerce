package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One product and how many of it an order wants held. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockLine {
    private String productId;
    private Integer quantity;
}
