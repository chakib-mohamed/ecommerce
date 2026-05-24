package the.chak.ecommerce.products.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    private String errorCode;
    private String message;
}
