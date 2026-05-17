package chakmed.ecommerce.products.boundary.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDate;

@Getter
@Setter
public class PromotionDto {

    private Long id;
    public String label;
    public Double percentageOff;

    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate activeFrom;

    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate activeTo;

    public ProductLiteDto product;
}

