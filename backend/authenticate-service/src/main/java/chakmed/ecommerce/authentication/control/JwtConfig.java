package chakmed.ecommerce.authentication.control;

import lombok.Data;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@Data
@ApplicationScoped
public class JwtConfig {
    @ConfigProperty(name = "security.jwt.uri", defaultValue = "/auth/**")
    private String Uri;

    @ConfigProperty(name = "security.jwt.header", defaultValue = "Authorization")
    private String header;

    @ConfigProperty(name = "security.jwt.prefix", defaultValue = "Bearer ")
    private String prefix;

    @ConfigProperty(name = "security.jwt.expiration", defaultValue = "15") // in minutes
    private int expiration;

    @ConfigProperty(name = "security.jwt.secret", defaultValue = "JwtSecretKey")
    private String secret;

}