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
    void getUsername_validToken_returnsUsername() {
        // given
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        // when
        Mono<String> result = tokenUtils.getUsername(validToken);

        // then
        StepVerifier.create(result).expectNext(username).verifyComplete();
    }

    @Test
    void getUsername_expiredToken_returnsEmpty() {
        // given
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken("testuser");
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        // when
        Mono<String> result = tokenUtils.getUsername(expiredToken);

        // then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void getUsername_invalidToken_returnsEmpty() {
        // given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        // when
        Mono<String> result = tokenUtils.getUsername(invalidToken);

        // then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void getUsername_revokedToken_returnsEmpty() {
        // given
        String validToken = TestJwtTokenGenerator.generateValidToken("testuser");
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        // when
        Mono<String> result = tokenUtils.getUsername(validToken);

        // then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void revokeToken_validToken_storesInRedis() {
        // given
        String validToken = TestJwtTokenGenerator.generateValidToken("testuser");
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // when
        Mono<Void> result = tokenUtils.revokeToken(validToken);

        // then
        StepVerifier.create(result).verifyComplete();
        verify(valueOperations).set(eq("revoked_token:" + validToken), eq("true"), any(Duration.class));
    }

    @Test
    void revokeToken_expiredToken_returnsEmpty() {
        // given
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken("testuser");
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // when
        Mono<Void> result = tokenUtils.revokeToken(expiredToken);

        // then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void revokeToken_invalidToken_usesFallback() {
        // given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // when
        Mono<Void> result = tokenUtils.revokeToken(invalidToken);

        // then
        StepVerifier.create(result).verifyComplete();
        verify(valueOperations).set(eq("revoked_token:" + invalidToken), eq("true"), any(Duration.class));
    }

    @Test
    void isTokenRevoked_revokedToken_returnsTrue() {
        // given
        String token = "some-token";
        when(redisTemplate.hasKey("revoked_token:" + token)).thenReturn(Mono.just(true));

        // when
        Mono<Boolean> result = tokenUtils.isTokenRevoked(token);

        // then
        StepVerifier.create(result).expectNext(true).verifyComplete();
    }

    @Test
    void isTokenRevoked_activeToken_returnsFalse() {
        // given
        String token = "some-token";
        when(redisTemplate.hasKey("revoked_token:" + token)).thenReturn(Mono.just(false));

        // when
        Mono<Boolean> result = tokenUtils.isTokenRevoked(token);

        // then
        StepVerifier.create(result).expectNext(false).verifyComplete();
    }
}
