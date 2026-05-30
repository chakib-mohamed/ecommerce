package the.chak.ecommerce.apigateway.control;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
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
		return tokenUtils.getUsername(authToken).map(username ->
				(Authentication) new UsernamePasswordAuthenticationToken(username, null, null))
				.doOnNext(auth -> log.info("JWT authenticated userId={}", auth.getName()));
	}
}
