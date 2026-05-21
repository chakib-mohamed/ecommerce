package the.chak.ecommerce.pricing.boundary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdatePriceRequest {
    @NotNull
    @Positive(message = "price must be positive")
    private Double price;
}
