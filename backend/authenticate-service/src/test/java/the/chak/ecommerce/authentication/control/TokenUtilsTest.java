package the.chak.ecommerce.authentication.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.control.exceptions.InvalidTokenException;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
class TokenUtilsTest {

    @Inject
    TokenUtils tokenUtils;

    @Inject
    RsaKeyProvider rsaKeyProvider;

    @Inject
    JwtConfig jwtConfig;

    @Test
    void generateToken_validSubject_returnsJwtWithMatchingSubject() {
        // when
        String token = tokenUtils.generateToken("alice@example.com");

        // then
        assertEquals("alice@example.com", tokenUtils.getUsername(token));
    }

    @Test
    void generateToken_validSubject_expiresAfterConfiguredMinutes() {
        // when
        String token = tokenUtils.generateToken("alice@example.com");

        // then
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(rsaKeyProvider.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        long expectedMs = (long) jwtConfig.getExpiration() * 60 * 1000L;
        long actualMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(actualMs - expectedMs) < 2000,
                "Expiration delta must be within 2 s of configured value");
    }

    @Test
    void getUsername_invalidToken_throwsInvalidTokenException() {
        assertThrows(InvalidTokenException.class,
                () -> tokenUtils.getUsername("this.is.not.a.jwt"));
    }

    @Test
    void getUsername_expiredToken_throwsInvalidTokenException() {
        // given — build a JWT whose expiration is already in the past
        long now = System.currentTimeMillis();
        String expiredToken = Jwts.builder()
                .setSubject("alice@example.com")
                .setIssuedAt(new Date(now - 120_000))
                .setExpiration(new Date(now - 60_000))
                .signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();

        // when / then
        assertThrows(InvalidTokenException.class,
                () -> tokenUtils.getUsername(expiredToken));
    }
}
