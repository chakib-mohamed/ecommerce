package the.chak.ecommerce.apigateway.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SecurityContextRepositoryTest {

    @Mock
    private ReactiveAuthenticationManager authenticationManager;

    @Mock
    private TokenResolver tokenResolver;

    @InjectMocks
    private SecurityContextRepository securityContextRepository;

    @Test
    void testLoad_ValidToken_ReturnsSecurityContext() {
        // Given
        String username = "testuser";
        String validToken = TestJwtTokenGenerator.generateValidToken(username);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        when(exchange.getRequest()).thenReturn(request);
        when(tokenResolver.resolveToken(request)).thenReturn(Optional.of(validToken));

        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, null);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(Mono.just(auth));

        // When
        Mono<SecurityContext> result = securityContextRepository.load(exchange);

        // Then
        StepVerifier.create(result).assertNext(securityContext -> {
            assertThat(securityContext).isNotNull();
            assertThat(securityContext.getAuthentication()).isNotNull();
            assertThat(securityContext.getAuthentication().getPrincipal()).isEqualTo(username);
        }).verifyComplete();
    }

    @Test
    void testLoad_NoToken_ReturnsEmpty() {
        // Given
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        when(exchange.getRequest()).thenReturn(request);
        when(tokenResolver.resolveToken(request)).thenReturn(Optional.empty());

        // When
        Mono<SecurityContext> result = securityContextRepository.load(exchange);

        // Then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testLoad_InvalidToken_ReturnsEmpty() {
        // Given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        when(exchange.getRequest()).thenReturn(request);
        when(tokenResolver.resolveToken(request)).thenReturn(Optional.of(invalidToken));
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<SecurityContext> result = securityContextRepository.load(exchange);

        // Then
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testSave_ThrowsUnsupportedOperationException() {
        // Given
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        // When/Then
        assertThatThrownBy(() -> securityContextRepository.save(exchange, securityContext))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage("Not supported yet.");
    }
}
