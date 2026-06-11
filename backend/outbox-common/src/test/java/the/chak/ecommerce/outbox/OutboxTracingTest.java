package the.chak.ecommerce.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the outbox trace carrier. These exercise the pure capture/extract contract with the
 * OTel API only (no SDK, no Kafka): a {@link Span#wrap wrapped} remote span context made current is
 * enough to drive {@code W3CTraceContextPropagator}, which is all {@link OutboxTracing} relies on.
 */
class OutboxTracingTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @Test
    @DisplayName("captures the active span as a traceparent and extracts the same trace back")
    void capturesAndExtracts_sameTrace() {
        Context active = Context.root().with(Span.wrap(SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));

        String traceparent;
        try (Scope ignored = active.makeCurrent()) {
            traceparent = OutboxTracing.currentTraceparent();
        }

        // the W3C wire form: version-traceid-spanid-sampledflag
        assertEquals("00-" + TRACE_ID + "-" + SPAN_ID + "-01", traceparent);

        SpanContext extracted = Span.fromContext(OutboxTracing.extract(traceparent)).getSpanContext();
        assertEquals(TRACE_ID, extracted.getTraceId());
        assertEquals(SPAN_ID, extracted.getSpanId());
        assertTrue(extracted.isSampled());
    }

    @Test
    @DisplayName("returns null when no span is in scope, so the relay publishes untraced as before")
    void currentTraceparent_isNull_withoutActiveSpan() {
        assertNull(OutboxTracing.currentTraceparent());
    }

    @Test
    @DisplayName("extract of a null/blank traceparent yields the root context (a fresh, unparented trace)")
    void extract_nullOrBlank_yieldsRoot() {
        assertSame(Context.root(), OutboxTracing.extract(null));
        assertSame(Context.root(), OutboxTracing.extract("   "));
        assertFalse(Span.fromContext(OutboxTracing.extract(null)).getSpanContext().isValid());
    }
}
