package the.chak.ecommerce.authentication.control;

import java.util.Date;
import java.util.List;
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

    /**
     * Mints an access token naming the user and the roles they hold.
     *
     * <p>The roles go in as {@code groups}, and the name is not a matter of taste: that is the claim
     * MicroProfile JWT maps to the container's roles, so it is the only one {@code @RolesAllowed}
     * will ever read. A claim called anything else parses cleanly, shows up in the token, and
     * authorizes nothing.
     *
     * <p>A user with no roles mints no claim at all rather than an empty array. Both authorize
     * nothing; an empty array would suggest roles were looked up and came back empty, and the
     * platform omits null fields rather than serializing them.
     *
     * @param roles the user's roles, or null if they have none
     */
    public String generateToken(String subject, List<String> roles) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtConfig.getExpiration() * 60 * 1000L));
        if (roles != null && !roles.isEmpty()) {
            builder.claim("groups", roles);
        }
        return builder.signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
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
