package the.chak.ecommerce.products.entity;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EmbeddedPromotion {
    private String label;
    private LocalDate activeFrom;
    private LocalDate activeTo;
    private Double percentageOff;
}
