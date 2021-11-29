package chakmed.ecommerce.authentication.control;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@ApplicationScoped
public class TokenUtils {

    @Inject
    JwtConfig jwtConfig;

    public String getUsername(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtConfig.getSecret().getBytes())
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

}
