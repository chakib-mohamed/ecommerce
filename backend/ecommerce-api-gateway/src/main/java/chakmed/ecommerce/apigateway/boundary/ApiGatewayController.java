package chakmed.ecommerce.apigateway.boundary;

import chakmed.ecommerce.apigateway.control.TokenUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/gateway")
public class ApiGatewayController {

    final
    TokenUtils tokenUtils;

    public ApiGatewayController(TokenUtils tokenUtils) {
        this.tokenUtils = tokenUtils;
    }

    @PostMapping("/revoke-token")
    public void revokeToken(@Valid @RequestBody RevokeTokenRequest revokeTokenRequest, @CookieValue(HttpHeaders.AUTHORIZATION) String tokenCookie) {

        String token = revokeTokenRequest.getToken();
        if("dummy".equals(token)) {
            token = tokenCookie;
        }

        tokenUtils.revokeToken(token);
    }



}
