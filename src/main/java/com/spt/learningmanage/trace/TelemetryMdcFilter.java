package com.spt.learningmanage.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Adds telemetry IDs to logs while preserving the existing application X-Trace-Id. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TelemetryMdcFilter extends OncePerRequestFilter {
    private final Tracer tracer;

    public TelemetryMdcFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null) {
            MDC.put("otelTraceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("otelTraceId");
            MDC.remove("spanId");
        }
    }
}
