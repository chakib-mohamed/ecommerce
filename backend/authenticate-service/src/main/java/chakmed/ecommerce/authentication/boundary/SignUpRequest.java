package chakmed.ecommerce.authentication.boundary;

import lombok.Data;

@Data
public class SignUpRequest {
    private String email;
    private String password;
}
