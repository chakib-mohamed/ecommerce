package the.chak.ecommerce.apigateway.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

class GatewayRequestLoggingFilterTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    @Test
    @DisplayName("Echoes the current span's traceId back as an X-Trace-Id response header")
    void filter_routedRequest_echoesTraceIdResponseHeader() {
        // given
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);

        GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter(tracer);
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/users/me").build());
        WebFilterChain chain = e -> Mono.empty();

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("Skips actuator paths without touching the tracer or adding a trace header")
    void filter_actuatorPath_skipsTracingAndPassesThrough() {
        // given
        Tracer tracer = mock(Tracer.class);
        GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter(tracer);
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/actuator/prometheus").build());
        WebFilterChain chain = e -> Mono.empty();

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNull();
        verifyNoInteractions(tracer);
    }

    @Test
    @DisplayName("Does not forward the retired X-Request-ID correlation header downstream")
    void filter_routedRequest_doesNotForwardRequestIdHeader() {
        // given
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);

        GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter(tracer);
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/api/users/me").build());

        WebFilterChain chain = e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-Request-ID")).isNull();
            return Mono.empty();
        };

        // when / then
        filter.filter(exchange, chain).block();
    }
}
