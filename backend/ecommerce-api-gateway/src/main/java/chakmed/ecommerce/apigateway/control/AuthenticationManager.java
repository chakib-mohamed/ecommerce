package chakmed.ecommerce.apigateway.control;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationManager implements ReactiveAuthenticationManager {

	private final TokenUtils tokenUtils;

	public AuthenticationManager(TokenUtils tokenUtils) {
		this.tokenUtils = tokenUtils;
	}

	@Override
	public Mono authenticate(Authentication authentication) {
		String authToken = authentication.getCredentials().toString();
		String username;
		try {
			username = tokenUtils.getUsername(authToken);
		} catch (Exception e) {
			log.error("exception when trying to decode the jwt token", e);
			username = null;
		}
		if (username != null) {
			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, null);
			SecurityContextHolder.getContext().setAuthentication(auth);
			return Mono.just(auth);
		} else {
			SecurityContextHolder.clearContext();
			return Mono.empty();
		}
	}
}