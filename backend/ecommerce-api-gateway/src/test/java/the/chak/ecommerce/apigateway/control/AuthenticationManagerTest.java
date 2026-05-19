package the.chak.ecommerce.apigateway.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthenticationManagerTest {

    @Mock
    private TokenUtils tokenUtils;

    @InjectMocks
    private AuthenticationManager authenticationManager;

    @Test
    void testAuthenticate_ValidToken_ReturnsAuthentication() {
        // Given
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(validToken, validToken);

        when(tokenUtils.getUsername(validToken)).thenReturn(Mono.just(username));

        // When
        Mono<Authentication> result = authenticationManager.authenticate(auth);

        // Then
        StepVerifier.create(result).assertNext(authentication -> {
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isEqualTo(username);
            assertThat(authentication.isAuthenticated()).isTrue();
        }).verifyComplete();
    }

    @Test
    void testAuthenticate_InvalidToken_ReturnsEmpty() {
        // Given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();
        Authentication auth = new UsernamePasswordAuthenticationToken(invalidToken, invalidToken);

        when(tokenUtils.getUsername(invalidToken))
                .thenReturn(Mono.empty());

        // When
        Mono<Authentication> result = authenticationManager.authenticate(auth);

        // Then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testAuthenticate_RevokedToken_ReturnsEmpty() {
        // Given
        String revokedToken = TestJwtTokenGenerator.generateValidToken("testuser");
        Authentication auth = new UsernamePasswordAuthenticationToken(revokedToken, revokedToken);

        when(tokenUtils.getUsername(revokedToken))
                .thenReturn(Mono.empty());

        // When
        Mono<Authentication> result = authenticationManager.authenticate(auth);

        // Then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testAuthenticate_ExpiredToken_ReturnsEmpty() {
        // Given
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken("testuser");
        Authentication auth = new UsernamePasswordAuthenticationToken(expiredToken, expiredToken);

        when(tokenUtils.getUsername(anyString()))
                .thenReturn(Mono.empty());

        // When
        Mono<Authentication> result = authenticationManager.authenticate(auth);

        // Then
        StepVerifier.create(result).verifyComplete();
    }
}
