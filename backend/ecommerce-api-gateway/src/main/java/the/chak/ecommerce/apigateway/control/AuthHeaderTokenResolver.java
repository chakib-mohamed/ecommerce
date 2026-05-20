package the.chak.ecommerce.apigateway.control;

import the.chak.ecommerce.apigateway.JwtConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("AuthHeaderTokenResolver")
public class AuthHeaderTokenResolver implements TokenResolver {

    private final JwtConfig jwtConfig;

    public AuthHeaderTokenResolver(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @Override
    public Optional<String> resolveToken(ServerHttpRequest serverHttpRequest) {
        String authHeader = serverHttpRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return Optional.ofNullable(authHeader)
                .filter(t -> t.startsWith(jwtConfig.getPrefix()))
                .map(t -> t.substring(jwtConfig.getPrefix().length()));
    }

}
