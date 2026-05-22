package the.chak.ecommerce.pricing.boundary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePriceRequest {
    @NotNull
    @Positive(message = "price must be positive")
    private Double price;
}
