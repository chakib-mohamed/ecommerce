package the.chak.ecommerce.authentication.control;

import java.util.Date;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.authentication.control.exceptions.InvalidTokenException;

@ApplicationScoped
public class TokenUtils {

    @Inject
    RsaKeyProvider rsaKeyProvider;

    @Inject
    JwtConfig jwtConfig;

    public String generateToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtConfig.getExpiration() * 60 * 1000L))
                .signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    public String getUsername(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(rsaKeyProvider.getPublicKey()).build()
                    .parseClaimsJws(token).getBody();
            return claims.getSubject();
        } catch (JwtException e) {
            throw new InvalidTokenException();
        }
    }
}
