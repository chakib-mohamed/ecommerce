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
    void resolveToken_validBearerHeader_returnsToken() {
        // given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        when(request.getHeaders()).thenReturn(headers);

        // when
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(token);
    }

    @Test
    void resolveToken_missingAuthorizationHeader_returnsEmpty() {
        // given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        // when
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveToken_wrongPrefix_returnsEmpty() {
        // given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
        when(request.getHeaders()).thenReturn(headers);

        // when
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveToken_emptyAuthorizationHeader_returnsEmpty() {
        // given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "");
        when(request.getHeaders()).thenReturn(headers);

        // when
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void resolveToken_onlyBearerPrefix_returnsEmptyString() {
        // given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer ");
        when(request.getHeaders()).thenReturn(headers);

        // when
        Optional<String> result = authHeaderTokenResolver.resolveToken(request);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }
}
