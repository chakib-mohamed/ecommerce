package the.chak.ecommerce.apigateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private List<String> allowedOrigins = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD");
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type");
    private boolean allowCredentials = false;
}
