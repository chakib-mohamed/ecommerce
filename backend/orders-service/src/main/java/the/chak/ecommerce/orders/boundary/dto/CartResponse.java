package the.chak.ecommerce.orders.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class CartResponse {
    private String userId;
    private List<CartItemResponse> items;
    private Instant updatedAt;
}
