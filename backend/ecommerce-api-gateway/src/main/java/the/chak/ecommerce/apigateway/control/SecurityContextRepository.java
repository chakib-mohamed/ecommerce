package the.chak.ecommerce.apigateway.control;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SecurityContextRepository implements ServerSecurityContextRepository {

	private final ReactiveAuthenticationManager authenticationManager;
	private final TokenResolver tokenResolver;

	public SecurityContextRepository(ReactiveAuthenticationManager authenticationManager,
			TokenResolver tokenResolver) {
		this.authenticationManager = authenticationManager;
		this.tokenResolver = tokenResolver;
	}

	@Override
	public Mono save(ServerWebExchange swe, SecurityContext sc) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Mono load(ServerWebExchange swe) {

		String token = tokenResolver.resolveToken(swe.getRequest()).orElse(null);

		if (token != null) {
			Authentication auth = new UsernamePasswordAuthenticationToken(token, token);
			return this.authenticationManager.authenticate(auth)
					.map((authentication) -> new SecurityContextImpl(authentication));
		} else {
			log.debug("couldn't resolve token .. gonna ignore.");
			return Mono.empty();
		}
	}

}
