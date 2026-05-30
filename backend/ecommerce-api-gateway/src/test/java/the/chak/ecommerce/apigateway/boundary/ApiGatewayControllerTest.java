package the.chak.ecommerce.apigateway.boundary;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import the.chak.ecommerce.apigateway.boundary.dto.RevokeTokenRequest;
import the.chak.ecommerce.apigateway.control.TokenUtils;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;

@WebFluxTest(ApiGatewayController.class)
@AutoConfigureWebTestClient
class ApiGatewayControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TokenUtils tokenUtils;

    @Test
    @WithMockUser
    @DisplayName("Returns 200 and revokes the token when the request is valid")
    void revokeToken_validRequest_returns200() {
        // given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken(token);
        when(tokenUtils.revokeToken(anyString())).thenReturn(Mono.empty());

        // when
        var result = webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange();

        // then
        result.expectStatus().isOk();
        verify(tokenUtils).revokeToken(token);
    }

    @Test
    @WithMockUser
    @DisplayName("Returns 400 when the revoke request carries an empty token")
    void revokeToken_emptyToken_returns400() {
        // given
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken("");

        // when
        var result = webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange();

        // then
        result.expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    @DisplayName("Returns 400 when the revoke request carries no token")
    void revokeToken_nullToken_returns400() {
        // given
        RevokeTokenRequest request = new RevokeTokenRequest();
        request.setToken(null);

        // when
        var result = webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post().uri("/api/gateway/revoke-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange();

        // then
        result.expectStatus().isBadRequest();
    }
}
