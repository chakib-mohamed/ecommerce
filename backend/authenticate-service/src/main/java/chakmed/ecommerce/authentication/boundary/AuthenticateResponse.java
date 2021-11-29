package chakmed.ecommerce.authentication.boundary;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthenticateResponse {
    private String accessToken;
}
