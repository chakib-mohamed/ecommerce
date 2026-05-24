package the.chak.ecommerce.products.boundary.dto;

import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class SavePromotionDto {

    private String id;

    @NotBlank
    private String productID;

    @NotBlank
    @Size(max = 100)
    private String label;

    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("100.0")
    private Double percentageOff;

    @NotNull
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeFrom;

    @NotNull
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeTo;
}
