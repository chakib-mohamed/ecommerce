package the.chak.ecommerce.apigateway;

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
    void testPublicEndpoint_Actuator_AllowsUnauthenticatedAccess() {
        // When/Then
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void testPublicEndpoint_GetProducts_AllowsUnauthenticatedAccess() {
        // When/Then - This will fail routing but should pass security
        webTestClient.get().uri("/api/products").exchange().expectStatus().is5xxServerError();
    }

    @Test
    void testPublicEndpoint_GetCategories_AllowsUnauthenticatedAccess() {
        // When/Then
        webTestClient.get().uri("/api/categories").exchange().expectStatus().is5xxServerError();
    }

    @Test
    void testPublicEndpoint_GetPromotions_AllowsUnauthenticatedAccess() {
        // When/Then
        webTestClient.get().uri("/api/promotions").exchange().expectStatus().is5xxServerError();
    }

    @Test
    void testPublicEndpoint_PostUsers_AllowsUnauthenticatedAccess() {
        // When/Then - This will fail routing but should pass security
        webTestClient.post().uri("/api/users").exchange().expectStatus().is5xxServerError(); // Service
                                                                                             // not
                                                                                             // available,
                                                                                             // but
                                                                                             // not
                                                                                             // 401/403
    }

    @Test
    void testProtectedEndpoint_NoToken_ReturnsUnauthorized() {
        // When/Then
        webTestClient.post().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void testProtectedEndpoint_ValidToken_AllowsAccess() {
        // Given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");

        // When/Then - This will fail routing but should pass security
        webTestClient.post().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().is5xxServerError(); // Service not available, but not
                                                               // 401/403
    }

    @Test
    void testProtectedEndpoint_InvalidToken_ReturnsUnauthorized() {
        // Given
        String invalidToken = TestJwtTokenGenerator.generateInvalidToken();

        // When/Then
        webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testProtectedEndpoint_ExpiredToken_ReturnsUnauthorized() {
        // Given
        String expiredToken = TestJwtTokenGenerator.generateExpiredToken("testuser");

        // When/Then
        webTestClient.post().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testProtectedEndpoint_MissingBearerPrefix_ReturnsUnauthorized() {
        // Given
        String token = TestJwtTokenGenerator.generateValidToken("testuser");

        // When/Then
        webTestClient.post().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, token) // Missing
                                                                                         // "Bearer
                                                                                         // " prefix
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void testOptionsRequest_AllowsUnauthenticatedAccess() {
        // When/Then - In this test environment, it returns 500 due to routing failure,
        // but it should not be 401/403
        webTestClient.options().uri("/api/orders").exchange().expectStatus().is5xxServerError();
    }
}
