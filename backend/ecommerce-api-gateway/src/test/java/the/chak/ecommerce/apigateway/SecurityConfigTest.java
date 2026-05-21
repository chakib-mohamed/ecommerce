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
    void actuatorHealth_noAuth_returns200() {
        // when
        var result = webTestClient.get().uri("/actuator/health").exchange();

        // then
        result.expectStatus().isOk();
    }

    @Test
    void getProducts_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/products").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    void getCategories_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/categories").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    void getPromotions_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.get().uri("/api/promotions").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    void postUsers_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.post().uri("/api/users").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }

    @Test
    void postOrders_noToken_returns401() {
        // when
        var result = webTestClient.post().uri("/api/orders").exchange();

        // then
        result.expectStatus().isUnauthorized();
    }

    @Test
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
    void optionsRequest_noAuth_passesSecurityLayer() {
        // when
        var result = webTestClient.options().uri("/api/orders").exchange();

        // then
        result.expectStatus().is5xxServerError(); // upstream unavailable in test env, not a security rejection
    }
}
