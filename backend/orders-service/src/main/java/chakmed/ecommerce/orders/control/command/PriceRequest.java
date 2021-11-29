package chakmed.ecommerce.orders.control.command;

import chakmed.ecommerce.orders.entity.OrderDTO;
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
