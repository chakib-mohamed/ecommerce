package the.chak.ecommerce.apigateway.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import the.chak.ecommerce.apigateway.JwtConfig;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;

@ExtendWith(MockitoExtension.class)
class AuthHeaderTokenResolverTest {

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private AuthHeaderTokenResolver authHeaderTokenResolver;

    @BeforeEach
    void setUp() {
        lenient().when(jwtConfig.getPrefix()).thenReturn("Bearer ");
    }

    @Test
    void testResolveToken_ValidAuthorizationHeader_ReturnsToken() {
        // Given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        String authHeader = "Bearer " + token;

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        when(request.getHeaders()).thenReturn(headers);

        // When
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(token);
    }

    @Test
    void testResolveToken_NoAuthorizationHeader_ReturnsEmpty() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getHeaders()).thenReturn(headers);

        // When
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testResolveToken_InvalidPrefix_ReturnsEmpty() {
        // Given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        String authHeader = "Basic " + token; // Wrong prefix

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        when(request.getHeaders()).thenReturn(headers);

        // When
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testResolveToken_EmptyAuthorizationHeader_ReturnsEmpty() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "");

        when(request.getHeaders()).thenReturn(headers);

        // When
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testResolveToken_OnlyBearerPrefix_ReturnsEmpty() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer ");

        when(request.getHeaders()).thenReturn(headers);

        // When
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }
}
