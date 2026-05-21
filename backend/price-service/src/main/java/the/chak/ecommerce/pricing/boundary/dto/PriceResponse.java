package the.chak.ecommerce.pricing.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
    private String productId;
    private Double price;
}
