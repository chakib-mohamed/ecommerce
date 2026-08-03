package the.chak.ecommerce.products.control.events;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PriceChangedEvent {
    private String productId;
    private BigDecimal newPrice;
}
