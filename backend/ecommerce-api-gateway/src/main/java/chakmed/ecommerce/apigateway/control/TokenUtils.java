package chakmed.ecommerce.apigateway.control;

import chakmed.ecommerce.apigateway.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TokenUtils {

    @Autowired
    JwtConfig jwtConfig;

    private Map<String, Date> revokedTokens = new ConcurrentHashMap<>();

    public String getUsername(String token) {

        if(this.isTokenRevoked(token)) {
            throw new RuntimeException("Token revoked");
        }
        Claims claims = Jwts.parser()
                .setSigningKey(jwtConfig.getSecret().getBytes())
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public void revokeToken(String token) {
        revokedTokens.put(token, new Date());
    }

    public boolean isTokenRevoked(String token) {
        return revokedTokens.containsKey(token);
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void purgeRevokedToken() {
        log.info("purging revoked token from cache ....");
        List<String> tokenToPurge = new ArrayList<>();
        revokedTokens.forEach((token, date) -> {
            // Test if token is already expired
            if(date.compareTo(Date.from(LocalDateTime.now().minusMinutes(jwtConfig.getExpiration()).atZone(ZoneId.systemDefault()).toInstant())) < 0) {
                log.info(String.format("purging token : %s", token ) );
                tokenToPurge.add(token);
            }
        });

        for (String token : tokenToPurge) {
            revokedTokens.remove(token);
        }
    }
}
