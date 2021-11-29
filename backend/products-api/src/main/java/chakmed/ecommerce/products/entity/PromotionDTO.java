package chakmed.ecommerce.products.entity;

import lombok.Getter;
import lombok.Setter;

import javax.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDate;

@Getter
@Setter
public class PromotionDTO {

    private Long id;
    public String label;
    public Double percentageOff;

    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate activeFrom;

    @JsonbDateFormat("yyyy-MM-dd")
    public LocalDate activeTo;

    public ProductLiteDTO product;
}

