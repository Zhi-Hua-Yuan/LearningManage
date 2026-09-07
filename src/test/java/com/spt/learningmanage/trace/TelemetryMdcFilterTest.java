package com.spt.learningmanage.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TelemetryMdcFilterTest {
    @Test
    void tagsTheSpanAndSharesTheResolvedApplicationTrace() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        io.micrometer.tracing.TraceContext context = mock(io.micrometer.tracing.TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("otel-trace");
        when(context.spanId()).thenReturn("otel-span");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(com.spt.learningmanage.trace.TraceContext.HEADER_NAME, "client_trace-123");

        new TelemetryMdcFilter(tracer).doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> assertEquals("client_trace-123",
                        request.getAttribute(com.spt.learningmanage.trace.TraceContext.REQUEST_ATTRIBUTE)));

        verify(span).tag("app.trace_id", "client_trace-123");
    }
}
