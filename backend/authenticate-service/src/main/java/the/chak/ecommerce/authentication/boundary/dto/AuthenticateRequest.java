package the.chak.ecommerce.authentication.boundary.dto;

import lombok.Data;

@Data
public class AuthenticateRequest {
    private String email;
    private String password;
}
