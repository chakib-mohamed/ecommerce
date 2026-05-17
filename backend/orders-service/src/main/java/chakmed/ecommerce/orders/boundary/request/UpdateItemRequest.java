package chakmed.ecommerce.orders.boundary.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateItemRequest {
    @Min(1)
    private int quantity;
}
