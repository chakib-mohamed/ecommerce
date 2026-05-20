package the.chak.ecommerce.apigateway.control;

import java.time.Duration;
import java.util.Date;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import the.chak.ecommerce.apigateway.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class TokenUtils {

    private final JwtConfig jwtConfig;
    private final RsaKeyProvider rsaKeyProvider;
    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenUtils(JwtConfig jwtConfig, RsaKeyProvider rsaKeyProvider,
            ReactiveStringRedisTemplate redisTemplate) {
        this.jwtConfig = jwtConfig;
        this.rsaKeyProvider = rsaKeyProvider;
        this.redisTemplate = redisTemplate;
    }

    private static final String REVOKED_TOKEN_PREFIX = "revoked_token:";

    public Mono<String> getUsername(String token) {
        return this.isTokenRevoked(token).flatMap(isRevoked -> {
            if (isRevoked) {
                log.warn("Token is revoked");
                return Mono.empty();
            }
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(rsaKeyProvider.getPublicKey()).build()
                        .parseSignedClaims(token).getPayload();

                return Mono.just(claims.getSubject());
            } catch (Exception e) {
                log.error("Error parsing token: {}", e.getMessage());
                return Mono.empty();
            }
        });
    }

    public Mono<Void> revokeToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(rsaKeyProvider.getPublicKey()).build()
                    .parseSignedClaims(token).getPayload();

            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();

            if (remainingTime > 0) {
                return redisTemplate.opsForValue()
                        .set(REVOKED_TOKEN_PREFIX + token, "true", Duration.ofMillis(remainingTime))
                        .then();
            }
            return Mono.empty();
        } catch (Exception e) {
            log.error("Error revoking token: {}", e.getMessage());
            return redisRevokeFallback(token);
        }
    }

    private Mono<Void> redisRevokeFallback(String token) {
        return redisTemplate.opsForValue().set(REVOKED_TOKEN_PREFIX + token, "true",
                Duration.ofMinutes(jwtConfig.getExpiration())).then();
    }

    public Mono<Boolean> isTokenRevoked(String token) {
        return redisTemplate.hasKey(REVOKED_TOKEN_PREFIX + token);
    }
}
