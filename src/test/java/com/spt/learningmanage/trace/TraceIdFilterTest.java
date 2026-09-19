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
        TraceContext.clear();
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

    /**
     * 回归守卫：micrometer 的 OTel 桥接会在请求处理中途（scope 附着/恢复时）
     * 用 OpenTelemetry 的 trace ID 覆盖 {@code MDC["traceId"]}。应用层 trace ID
     * 必须仍然可读，否则 {@code ai_call_log.trace_id} 等审计字段会与响应头矛盾。
     */
    @Test
    void applicationTraceIdSurvivesTelemetryMdcClobberingMidRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER_NAME, "s3_TO-REG-019_a7a49946621b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            MDC.put(TraceContext.MDC_KEY, "3d62c4a7f0076d5bc008176ce056448c");
            assertEquals("s3_TO-REG-019_a7a49946621b", TraceContext.currentOrCreate());
            assertEquals("s3_TO-REG-019_a7a49946621b", TraceContext.explicitOrCurrent(null));
        });

        assertEquals("s3_TO-REG-019_a7a49946621b", response.getHeader(TraceContext.HEADER_NAME));
    }

    @Test
    void bindingIsClearedAfterRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.HEADER_NAME, "s3_TO-REG-019_a7a49946621b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertTrue(TraceContext.currentOrCreate().matches("[a-f0-9]{32}"));
    }
}
