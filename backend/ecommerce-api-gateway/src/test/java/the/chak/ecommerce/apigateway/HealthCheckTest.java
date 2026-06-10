package the.chak.ecommerce.apigateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
// Boot disables metrics export in tests by default; enable metrics (not tracing) so the
// Prometheus scrape endpoint is registered for the exposure assertion below.
@AutoConfigureObservability(tracing = false, metrics = true)
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class HealthCheckTest {

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
    @DisplayName("Returns 200 on the aggregate actuator health endpoint")
    void health_returns200() {
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Returns 200 on the liveness probe")
    void liveness_returns200() {
        webTestClient.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Returns 200 on the readiness probe")
    void readiness_returns200() {
        webTestClient.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Exposes the Prometheus scrape endpoint")
    void prometheus_returns200() {
        webTestClient.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk();
    }
}
