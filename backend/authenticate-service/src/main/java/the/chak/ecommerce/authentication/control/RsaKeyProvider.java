package the.chak.ecommerce.authentication.control;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

@ApplicationScoped
@Getter
public class RsaKeyProvider {

    @Inject
    JwtConfig jwtConfig;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void init() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            byte[] privateBytes = Base64.getDecoder().decode(strip(jwtConfig.getPrivateKeyBase64()));
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

            byte[] publicBytes = Base64.getDecoder().decode(strip(jwtConfig.getPublicKeyBase64()));
            publicKey = kf.generatePublic(new X509EncodedKeySpec(publicBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair from config", e);
        }
    }

    private String strip(String pem) {
        return pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
    }
}
