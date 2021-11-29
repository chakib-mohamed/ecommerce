package chakmed.ecommerce.apigateway.boundary;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
    public class RevokeTokenRequest {
        @NotEmpty
        private String token;
    }