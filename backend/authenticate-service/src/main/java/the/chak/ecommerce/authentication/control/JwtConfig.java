package the.chak.ecommerce.authentication.control;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;

@Data
@ApplicationScoped
public class JwtConfig {
    @ConfigProperty(name = "security.jwt.uri", defaultValue = "/auth/**")
    private String uri;

    @ConfigProperty(name = "security.jwt.header", defaultValue = "Authorization")
    private String header;

    @ConfigProperty(name = "security.jwt.prefix", defaultValue = "Bearer ")
    private String prefix;

    @ConfigProperty(name = "security.jwt.expiration", defaultValue = "15") // in minutes
    private int expiration;

    @ConfigProperty(name = "security.jwt.private-key")
    private String privateKeyBase64;

    @ConfigProperty(name = "security.jwt.public-key")
    private String publicKeyBase64;

}
