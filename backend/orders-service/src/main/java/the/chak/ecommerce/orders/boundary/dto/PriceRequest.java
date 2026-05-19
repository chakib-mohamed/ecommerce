package the.chak.ecommerce.orders.boundary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PriceRequest {

    private String id;
    private OrderDTO order;

    public PriceRequest(OrderDTO order) {
        this.order = order;
    }
}
