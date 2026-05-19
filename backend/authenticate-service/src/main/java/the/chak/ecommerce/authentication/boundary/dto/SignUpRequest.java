package the.chak.ecommerce.authentication.boundary.dto;

import lombok.Data;

@Data
public class SignUpRequest {
    private String email;
    private String password;
}
