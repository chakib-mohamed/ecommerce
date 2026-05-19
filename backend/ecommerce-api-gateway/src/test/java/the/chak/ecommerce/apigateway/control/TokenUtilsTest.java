package the.chak.ecommerce.apigateway.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import the.chak.ecommerce.apigateway.JwtConfig;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TokenUtilsTest {

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private RsaKeyProvider rsaKeyProvider;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenUtils tokenUtils;

    @BeforeEach
    void setUp() {
        lenient().when(rsaKeyProvider.getPublicKey()).thenReturn(TestJwtTokenGenerator.getTestPublicKey());
        lenient().when(jwtConfig.getExpiration()).thenReturn(15);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGetUsername_ValidToken_ReturnsUsername() {
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        Mono<String> result = tokenUtils.getUsername(validToken);

        StepVerifier.create(result).expectNext(username).verifyComplete();
    }

    @Test
    void testGetUsername_ExpiredToken_ReturnsEmpty() {
        String username = "testuser";
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken(username);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        Mono<String> result = tokenUtils.getUsername(expiredToken);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testGetUsername_InvalidToken_ReturnsEmpty() {
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        Mono<String> result = tokenUtils.getUsername(invalidToken);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testGetUsername_RevokedToken_ReturnsEmpty() {
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        Mono<String> result = tokenUtils.getUsername(validToken);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testRevokeToken_ValidToken_StoresInRedis() {
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mono<Void> result = tokenUtils.revokeToken(validToken);

        StepVerifier.create(result).verifyComplete();
        verify(valueOperations).set(eq("revoked_token:" + validToken), eq("true"),
                any(Duration.class));
    }

    @Test
    void testRevokeToken_ExpiredToken_ReturnsEmpty() {
        String username = "testuser";
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken(username);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mono<Void> result = tokenUtils.revokeToken(expiredToken);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testRevokeToken_InvalidToken_UsesFallback() {
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mono<Void> result = tokenUtils.revokeToken(invalidToken);

        StepVerifier.create(result).verifyComplete();
        verify(valueOperations).set(eq("revoked_token:" + invalidToken), eq("true"),
                any(Duration.class));
    }

    @Test
    void testIsTokenRevoked_RevokedToken_ReturnsTrue() {
        String token = "some-token";
        when(redisTemplate.hasKey("revoked_token:" + token)).thenReturn(Mono.just(true));

        Mono<Boolean> result = tokenUtils.isTokenRevoked(token);

        StepVerifier.create(result).expectNext(true).verifyComplete();
    }

    @Test
    void testIsTokenRevoked_NotRevokedToken_ReturnsFalse() {
        String token = "some-token";
        when(redisTemplate.hasKey("revoked_token:" + token)).thenReturn(Mono.just(false));

        Mono<Boolean> result = tokenUtils.isTokenRevoked(token);

        StepVerifier.create(result).expectNext(false).verifyComplete();
    }
}
