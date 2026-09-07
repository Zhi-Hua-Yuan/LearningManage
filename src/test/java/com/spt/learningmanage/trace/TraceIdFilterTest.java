package com.spt.learningmanage.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldReuseValidIncomingTraceIdAndClearMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER_NAME, "client_trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MDC.put(TraceContext.MDC_KEY, "otel-generated-trace");
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertEquals("client_trace-123", MDC.get(TraceContext.MDC_KEY)));

        assertEquals("client_trace-123", response.getHeader(TraceContext.HEADER_NAME));
        assertNull(MDC.get(TraceContext.MDC_KEY));
    }

    @Test
    void shouldReplaceInvalidIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER_NAME, "bad trace id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(TraceContext.HEADER_NAME);
        assertTrue(generated.matches("[a-f0-9]{32}"));
        assertNull(MDC.get(TraceContext.MDC_KEY));
    }

    @Test
    void applicationTraceFilterRunsAfterTelemetryMdcCapture() {
        int applicationOrder = TraceIdFilter.class.getAnnotation(Order.class).value();
        int telemetryOrder = TelemetryMdcFilter.class.getAnnotation(Order.class).value();

        assertTrue(applicationOrder > telemetryOrder);
    }
}
