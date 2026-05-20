package the.chak.ecommerce.apigateway.boundary;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import the.chak.ecommerce.apigateway.boundary.dto.RevokeTokenRequest;
import the.chak.ecommerce.apigateway.control.TokenUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@RestController
@RequestMapping("/api/gateway")
public class ApiGatewayController {

    final TokenUtils tokenUtils;

    public ApiGatewayController(TokenUtils tokenUtils) {
        this.tokenUtils = tokenUtils;
    }

    @PostMapping("/revoke-token")
    public Mono<Void> revokeToken(@Valid @RequestBody RevokeTokenRequest revokeTokenRequest) {

        log.info("Revoking token [{}...]", revokeTokenRequest.getToken().substring(0, Math.min(8, revokeTokenRequest.getToken().length())));
        return tokenUtils.revokeToken(revokeTokenRequest.getToken());
    }



}
