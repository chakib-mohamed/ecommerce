package the.chak.ecommerce.products.boundary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotBlank
    private String productId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer stars;

    @Size(max = 2000)
    private String text;
}
