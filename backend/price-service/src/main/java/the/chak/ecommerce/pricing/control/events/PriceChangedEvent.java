package the.chak.ecommerce.pricing.control.events;

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
    private Double newPrice;
}
