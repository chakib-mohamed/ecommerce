package chakmed.ecommerce.products.boundary.dto;

import lombok.Data;

import jakarta.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDate;

@Data
public class SavePromotionDto {

    private String id;
    private String productID;
    private String label;
    private Double percentageOff;
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeFrom;
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeTo;
}
