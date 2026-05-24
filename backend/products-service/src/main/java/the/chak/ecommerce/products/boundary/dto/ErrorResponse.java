package the.chak.ecommerce.products.boundary.dto;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String type;
    @JsonbProperty("errorCode")
    private String errorCode;
    private String message;
}
