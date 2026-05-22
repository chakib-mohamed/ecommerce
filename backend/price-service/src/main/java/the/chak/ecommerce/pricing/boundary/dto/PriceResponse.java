package the.chak.ecommerce.pricing.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
    private String productId;
    private Double price;
}
