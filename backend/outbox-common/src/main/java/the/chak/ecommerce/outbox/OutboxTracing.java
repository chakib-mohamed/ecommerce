package the.chak.ecommerce.outbox;

import java.util.HashMap;
import java.util.Map;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Carries the originating trace across the transactional outbox so the off-request publish stays in
 * the same trace tree as the HTTP request that produced the event.
 *
 * <p>The relay drains the outbox on a background thread, seconds after the request span ended, so
 * there is no active OpenTelemetry context at publish time and the auto-instrumented Kafka producer
 * would otherwise start a brand-new trace. To bridge that gap we snapshot the request's W3C
 * {@code traceparent} at write time (where {@link Context#current()} is still the request context),
 * store it on the outbox record, and rebuild a {@link Context} from it at publish time to parent the
 * producer span - the standard outbox-tracing approach.
 *
 * <p>Only the {@code traceparent} is carried; {@code tracestate} is intentionally omitted - it is
 * empty under this stack's config (an {@code always_on} sampler and no vendor tracestate), and
 * dropping it keeps the at-rest form to a single column.
 */
public final class OutboxTracing {

    private static final String TRACEPARENT = "traceparent";

    private static final W3CTraceContextPropagator PROPAGATOR =
            W3CTraceContextPropagator.getInstance();

    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    private static final TextMapGetter<Map<String, String>> GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier == null ? null : carrier.get(key);
                }
            };

    private OutboxTracing() {
    }

    /**
     * Snapshots the current trace as a W3C {@code traceparent}. Returns {@code null} when there is no
     * valid, sampled span to propagate (e.g. tests with the OTel SDK disabled, or a publish with no
     * active span) - the propagator writes nothing in that case, so the relay simply publishes
     * untraced, exactly as before this fix.
     */
    public static String currentTraceparent() {
        Map<String, String> carrier = new HashMap<>(1);
        PROPAGATOR.inject(Context.current(), carrier, SETTER);
        return carrier.get(TRACEPARENT);
    }

    /**
     * Rebuilds the parent {@link Context} from a stored {@code traceparent}. A {@code null}/blank
     * value (an old record written before this fix, or one captured without an active span) yields
     * {@link Context#root()}, which parents a fresh root span - the pre-fix behavior.
     */
    public static Context extract(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Context.root();
        }
        Map<String, String> carrier = new HashMap<>(1);
        carrier.put(TRACEPARENT, traceparent);
        return PROPAGATOR.extract(Context.root(), carrier, GETTER);
    }
}
