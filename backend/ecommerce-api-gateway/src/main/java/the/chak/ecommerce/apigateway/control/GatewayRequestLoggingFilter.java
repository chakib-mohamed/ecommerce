package the.chak.ecommerce.apigateway.control;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Logs one INFO line per inbound request once the security context is resolved, so the
 * authenticated username is available, and echoes the request's {@code traceId} back to the
 * caller as an {@code X-Trace-Id} response header for client-facing correlation.
 *
 * <p>Correlation is owned by OpenTelemetry: {@code traceId}/{@code spanId} are in the MDC
 * (and the log pattern) for every request, and the OTLP exporter propagates context
 * downstream via W3C {@code traceparent}. There is no longer a hand-rolled {@code X-Request-ID}.
 * Actuator paths are suppressed.
 */
@Slf4j
@Component
public class GatewayRequestLoggingFilter implements WebFilter, Ordered {

	static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final Tracer tracer;

	public GatewayRequestLoggingFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (path.startsWith("/actuator")) {
			return chain.filter(exchange);
		}

		String method = exchange.getRequest().getMethod().name();
		return ReactiveSecurityContextHolder.getContext()
				.map(ctx -> ctx.getAuthentication().getName())
				.defaultIfEmpty("anonymous")
				.doOnNext(userId -> {
					Span span = tracer.currentSpan();
					if (span != null) {
						exchange.getResponse().getHeaders()
								.set(TRACE_ID_HEADER, span.context().traceId());
					}
					log.info("Routing {} {} userId={}", method, path, userId);
				})
				.then(chain.filter(exchange));
	}

	@Override
	public int getOrder() {
		// Run after the Spring Security filter chain so the principal is resolved,
		// but still ahead of the gateway's GlobalFilters.
		return Ordered.LOWEST_PRECEDENCE;
	}
}
