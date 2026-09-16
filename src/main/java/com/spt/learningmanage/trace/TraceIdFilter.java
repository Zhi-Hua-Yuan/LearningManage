package com.spt.learningmanage.trace;

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

@Component
// 在 Micrometer 的 observation filter 之后执行。TelemetryMdcFilter 先复制标准 span
// ID，然后此过滤器恢复应用层的 trace ID，确保 OTel 的 MDC 关联不会覆盖现有的
// X-Trace-Id 约定。
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Object resolved = request.getAttribute(TraceContext.REQUEST_ATTRIBUTE);
        String traceId = resolved instanceof String value
                ? value : TraceContext.resolve(request.getHeader(TraceContext.HEADER_NAME));
        MDC.put(TraceContext.MDC_KEY, traceId);
        response.setHeader(TraceContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }
}
