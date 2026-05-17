package chakmed.ecommerce.orders.boundary.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddItemRequest {
    @NotBlank
    private String productId;

    @Min(1)
    private int quantity;
}
