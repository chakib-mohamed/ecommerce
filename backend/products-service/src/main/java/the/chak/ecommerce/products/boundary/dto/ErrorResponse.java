package the.chak.ecommerce.products.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    private String message;
}
