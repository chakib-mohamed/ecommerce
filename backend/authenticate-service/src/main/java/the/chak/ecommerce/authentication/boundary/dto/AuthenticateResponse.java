package the.chak.ecommerce.authentication.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticateResponse {
    private String accessToken;
}
