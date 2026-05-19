package the.chak.ecommerce.pricing.boundary.dto;

import lombok.Data;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;

@Data
public class PriceCalculationRequest {
    private String id;
    private OrderDTO order;
}
