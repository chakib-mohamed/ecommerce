package the.chak.ecommerce.orders.boundary.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PriceRequest {

    private String id;
    private OrderDTO order;

    public PriceRequest(OrderDTO order) {
        this.order = order;
    }
}
