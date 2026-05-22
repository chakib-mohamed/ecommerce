package the.chak.ecommerce.pricing.boundary.dto;

import lombok.Getter;
import lombok.Setter;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;

@Getter
@Setter
public class PriceCalculationRequest {
    private String id;
    private OrderDTO order;
}
