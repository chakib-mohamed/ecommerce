package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceChangedEvent {
    private String productId;
    private Double newPrice;
}
