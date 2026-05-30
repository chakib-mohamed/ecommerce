package the.chak.ecommerce.apigateway.control;

import java.util.Optional;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Logs one INFO line per inbound request once the security context is resolved, so the
 * authenticated username is available. This filter is the single source of truth for the
 * correlation id: it resolves {@code X-Request-ID} (reusing a client-supplied value or
 * generating one), stores it on the exchange, and stamps it on the request so the
 * downstream-forwarding {@link RequestIdGlobalFilter} reuses the same value.
 *
 * <p>WebFlux is reactive and MDC does not propagate across reactor threads, so the
 * {@code requestId} is logged explicitly rather than via an MDC pattern. Actuator paths
 * are suppressed.
 */
@Slf4j
@Component
public class GatewayRequestLoggingFilter implements WebFilter, Ordered {

	static final String REQUEST_ID_HEADER = "X-Request-ID";
	static final String REQUEST_ID_ATTRIBUTE = "requestId";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (path.startsWith("/actuator")) {
			return chain.filter(exchange);
		}

		String requestId = Optional
				.ofNullable(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER))
				.filter(id -> !id.isBlank())
				.orElseGet(() -> UUID.randomUUID().toString());

		ServerWebExchange mutated = exchange.mutate()
				.request(r -> r.headers(h -> h.set(REQUEST_ID_HEADER, requestId)))
				.build();
		mutated.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);

		String method = mutated.getRequest().getMethod().name();
		return ReactiveSecurityContextHolder.getContext()
				.map(ctx -> ctx.getAuthentication().getName())
				.defaultIfEmpty("anonymous")
				.doOnNext(userId -> log.info("Routing {} {} userId={} requestId={}",
						method, path, userId, requestId))
				.then(chain.filter(mutated));
	}

	@Override
	public int getOrder() {
		// Run after the Spring Security filter chain so the principal is resolved,
		// but still ahead of the gateway's GlobalFilters.
		return Ordered.LOWEST_PRECEDENCE;
	}
}
