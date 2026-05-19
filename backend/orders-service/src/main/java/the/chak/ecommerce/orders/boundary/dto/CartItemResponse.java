package the.chak.ecommerce.orders.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CartItemResponse {
    private String productId;
    private int quantity;
    private Double unitPrice;
    private Double totalPrice;
}
