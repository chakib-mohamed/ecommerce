package the.chak.ecommerce.pricing.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationResponse {
    private String id;
    private OrderDTO order;
}
