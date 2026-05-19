package the.chak.ecommerce.products.boundary.dto;

import java.time.LocalDate;
import jakarta.json.bind.annotation.JsonbDateFormat;
import lombok.Getter;
import lombok.Setter;

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
