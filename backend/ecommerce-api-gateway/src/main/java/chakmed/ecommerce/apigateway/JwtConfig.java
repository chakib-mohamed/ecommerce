package chakmed.ecommerce.apigateway;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtConfig {
    @Value("${security.jwt.uri:/api/users/authenticate/**}")
    String Uri;

    @Value("${security.jwt.header:Authorization}")
    String header;

    @Value("${security.jwt.prefix:Bearer }")
    String prefix;

    @Value("${security.jwt.expiration:15}")
    int expiration;

    @Value("${security.jwt.secret:JwtSecretKey}")
    String secret;

}