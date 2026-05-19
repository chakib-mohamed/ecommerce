package the.chak.ecommerce.apigateway.control;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import the.chak.ecommerce.apigateway.JwtConfig;
import lombok.Getter;

@Component
@Getter
public class RsaKeyProvider {

    @Autowired
    JwtConfig jwtConfig;

    private PublicKey publicKey;

    @PostConstruct
    void init() {
        try {
            String stripped = jwtConfig.getPublicKeyBase64()
                    .replaceAll("-----[^-]+-----", "")
                    .replaceAll("\\s+", "");
            byte[] bytes = Base64.getDecoder().decode(stripped);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key from config", e);
        }
    }
}
