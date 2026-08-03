package the.chak.ecommerce.orders.boundary.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CartItemResponse {
    private String productId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
