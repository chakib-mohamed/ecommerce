package the.chak.ecommerce.apigateway.control;

import java.util.Optional;
import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Ensures every request routed downstream carries an {@code X-Request-ID} header so logs
 * can be correlated across services. Reuses the id resolved by
 * {@link GatewayRequestLoggingFilter} (stored on the exchange) or, defensively, the
 * incoming header — generating one only if neither is present.
 */
@Slf4j
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String requestId = Optional
				.ofNullable((String) exchange
						.getAttribute(GatewayRequestLoggingFilter.REQUEST_ID_ATTRIBUTE))
				.or(() -> Optional.ofNullable(exchange.getRequest().getHeaders()
						.getFirst(GatewayRequestLoggingFilter.REQUEST_ID_HEADER)))
				.filter(id -> !id.isBlank())
				.orElseGet(() -> UUID.randomUUID().toString());

		log.info("Request received requestId={} — forwarding X-Request-ID", requestId);

		return chain.filter(exchange.mutate()
				.request(r -> r.headers(h -> h
						.set(GatewayRequestLoggingFilter.REQUEST_ID_HEADER, requestId)))
				.build());
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
