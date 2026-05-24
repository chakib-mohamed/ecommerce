package the.chak.ecommerce.authentication.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    private String errorCode;
    private String message;
}
