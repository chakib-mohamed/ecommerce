package the.chak.ecommerce.orders.boundary.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVO {
    @NotBlank
    private String productID;
    private String title;
    @NotNull
    @Min(1)
    private Integer qty;
    @NotNull
    @Positive
    private BigDecimal price;

    /** A percentage, not an amount - stays a plain number. */
    private Double percentageOff;
}
