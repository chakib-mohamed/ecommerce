package the.chak.ecommerce.pricing.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    private String message;
}
