package the.chak.ecommerce.apigateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import the.chak.ecommerce.apigateway.util.TestJwtTokenGenerator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class SecurityConfigTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Returns 200 on the actuator health endpoint without authentication")
    void actuatorHealth_noAuth_returns200() {
        // when
        var result = webTestClient.get().uri("/actuator/health").exchange();

        // then
        result.expectStatus().isOk();
    }

    @Test
    @DisplayName("Lets an unauthenticated GET /api/products through the security layer to the upstream")
    void getProducts_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/products").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    @DisplayName("Lets an unauthenticated GET /api/categories through the security layer to the upstream")
    void getCategories_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/categories").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    @DisplayName("Lets an unauthenticated GET /api/promotions through the security layer to the upstream")
    void getPromotions_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/promotions").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    @DisplayName("Lets an unauthenticated POST /api/users through the security layer to the upstream")
    void postUsers_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.post().uri("/api/users").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    @DisplayName("Returns 401 for POST /api/orders without a token")
    void postOrders_noToken_returns401() {
        // when
        var result = webTestClient.post().uri("/api/orders").exchange();

        // then
        result.expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Lets POST /api/orders with a valid token through the security layer to the upstream")
    void postOrders_validToken_passesSecurityLayer() {
        // given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");

        // when
        var result = webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    @DisplayName("Returns 401 for POST /api/orders with an invalid token")
    void postOrders_invalidToken_returns401() {
        // given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();

        // when
        var result = webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .exchange();

        // then
        result.expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Returns 401 for POST /api/orders with an expired token")
    void postOrders_expiredToken_returns401() {
        // given
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken("testuser");

        // when
        var result = webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange();

        // then
        result.expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Returns 401 for POST /api/orders when the token lacks the Bearer prefix")
    void postOrders_missingBearerPrefix_returns401() {
        // given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");

        // when
        var result = webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange();

        // then
        result.expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Lets an unauthenticated OPTIONS preflight request through the security layer")
    void optionsRequest_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.options().uri("/api/orders").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }
}
