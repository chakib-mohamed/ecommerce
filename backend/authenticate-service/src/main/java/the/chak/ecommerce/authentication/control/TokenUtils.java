package the.chak.ecommerce.authentication.control;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenUtils {

    @Inject
    RsaKeyProvider rsaKeyProvider;

    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(rsaKeyProvider.getPublicKey()).build()
                .parseClaimsJws(token).getBody();

        return claims.getSubject();
    }

}
