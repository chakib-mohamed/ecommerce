package the.chak.ecommerce.apigateway.boundary;

import the.chak.ecommerce.apigateway.boundary.dto.RevokeTokenRequest;
import the.chak.ecommerce.apigateway.control.TokenUtils;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(ApiGatewayController.class)
@AutoConfigureWebTestClient
class ApiGatewayControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TokenUtils tokenUtils;

    @Test
    @WithMockUser
    void testRevokeToken_ValidRequest_ReturnsOk() {
        // Given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken(token);

        when(tokenUtils.revokeToken(anyString())).thenReturn(Mono.empty());

        // When/Then
        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                .expectStatus().isOk();

        verify(tokenUtils).revokeToken(token);
    }

    @Test
    @WithMockUser
    void testRevokeToken_EmptyToken_ReturnsBadRequest() {
        // Given
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken("");

        // When/Then
        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    void testRevokeToken_NullToken_ReturnsBadRequest() {
        // Given
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken(null);

        // When/Then
        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                .expectStatus().isBadRequest();
    }
}
