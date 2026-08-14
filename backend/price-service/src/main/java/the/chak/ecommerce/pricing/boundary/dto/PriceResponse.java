package the.chak.ecommerce.pricing.boundary.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
    private String productId;
    private BigDecimal price;
}
