package the.chak.ecommerce.apigateway.boundary.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class RevokeTokenRequest {
    @NotEmpty
    private String token;
}
