package chakmed.ecommerce.orders.boundary.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class CartResponse {
    private String userId;
    private List<CartItemResponse> items;
    private Instant updatedAt;
}
