package chakmed.ecommerce.products.boundary.command;

import lombok.Data;

import javax.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDate;

@Data
public class CreatePromotionCommand {

    private String id;
    private String productID;
    private String label;
    private Double percentageOff;
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeFrom;
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate activeTo;
}
