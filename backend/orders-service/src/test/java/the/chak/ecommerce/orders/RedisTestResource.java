package the.chak.ecommerce.orders;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestResource implements QuarkusTestResourceLifecycleManager {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Override
    public Map<String, String> start() {
        REDIS.start();
        return Map.of("quarkus.redis.hosts",
                "redis://localhost:" + REDIS.getMappedPort(6379));
    }

    @Override
    public void stop() {
        REDIS.stop();
    }
}
