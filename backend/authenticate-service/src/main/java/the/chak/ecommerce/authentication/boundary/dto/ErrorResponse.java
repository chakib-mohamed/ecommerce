package the.chak.ecommerce.authentication.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    private String message;
}
