package com.fraudplatform.alerts.infrastructure.kafka;

import com.fraudplatform.contracts.EventHeaders;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;

/**
 * Captures the <i>current</i> trace context (W3C traceparent) and request id as message headers.
 * Called while writing the outbox row, inside the request that caused it, so the event is linked
 * to that request's trace even though the relay sends it later, from another thread.
 */
public class CurrentPropagationHeaders implements Supplier<Map<String, String>> {

    private final Tracer tracer;
    private final Propagator propagator;

    public CurrentPropagationHeaders(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Map<String, String> get() {
        Map<String, String> headers = new HashMap<>();
        Span span = tracer.currentSpan();
        if (span != null) {
            propagator.inject(span.context(), headers, Map::put);
        }
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            headers.put(EventHeaders.REQUEST_ID, requestId);
        }
        return headers;
    }
}
