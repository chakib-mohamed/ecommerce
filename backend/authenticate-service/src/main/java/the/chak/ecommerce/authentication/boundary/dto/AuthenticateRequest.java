package the.chak.ecommerce.authentication.boundary.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthenticateRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
