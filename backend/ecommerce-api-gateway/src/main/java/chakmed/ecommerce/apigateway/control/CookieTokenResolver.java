package chakmed.ecommerce.apigateway.control;

import chakmed.ecommerce.apigateway.JwtConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.Optional;

@Service("CookieTokenResolver")
public class CookieTokenResolver implements TokenResolver {

    @Autowired
    JwtConfig jwtConfig;

    @Override
    public Optional<String> resolveToken(ServerHttpRequest serverHttpRequest) {
        return getAuthorizationCookie(serverHttpRequest) ;//.filter(t -> t.startsWith(jwtConfig.getPrefix())).map(t -> t.replace(jwtConfig.getPrefix(), ""));
    }

    private Optional<String> getAuthorizationCookie(ServerHttpRequest request){
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();

        return Optional.ofNullable(cookies.getFirst(HttpHeaders.AUTHORIZATION)).map(HttpCookie::getValue);
    }

}
