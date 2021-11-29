package chakmed.ecommerce.apigateway.control;

import chakmed.ecommerce.apigateway.JwtConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("AuthHeaderTokenResolver")
public class AuthHeaderTokenResolver implements TokenResolver {

    @Autowired
    JwtConfig jwtConfig;

    @Override
    public Optional<String> resolveToken(ServerHttpRequest serverHttpRequest) {
        String authHeader = serverHttpRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return Optional.ofNullable(authHeader).filter(t -> t.startsWith(jwtConfig.getPrefix())).map(t -> t.replace(jwtConfig.getPrefix(), ""));
    }

}
