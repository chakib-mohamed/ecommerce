package chakmed.ecommerce.apigateway.control;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Optional;

public interface TokenResolver {
    Optional<String> resolveToken(ServerHttpRequest serverHttpRequest);
}
