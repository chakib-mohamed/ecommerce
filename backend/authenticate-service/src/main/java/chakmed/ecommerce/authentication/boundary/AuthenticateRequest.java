package chakmed.ecommerce.authentication.boundary;

import lombok.Data;

@Data
public class AuthenticateRequest {
    private String email;
    private String password;
}
