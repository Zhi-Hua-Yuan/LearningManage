package com.spt.learningmanage.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = TraceContext.class.getName() + ".applicationTraceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    /**
     * 应用层 trace ID 的线程级绑定，生命周期与请求一致。
     *
     * <p>只读 MDC 是不够的：micrometer-tracing 的 OTel 桥接会在每次 scope 附着/恢复时
     * 用 OpenTelemetry 的 trace ID 覆盖 {@code MDC["traceId"]}
     * （见 {@code io.micrometer.tracing.otel.bridge.Slf4JEventListener}）。它是按
     * 「scope 事件」触发的，而 scope 会在请求处理中途（首次 JDBC/出站调用产生子 span 时）
     * 再次附着，因此**在请求后半程读 MDC 会拿到遥测 ID，而不是客户端传入的 X-Trace-Id**，
     * 导致 {@code ai_call_log.trace_id} 等审计字段与响应头互相矛盾。
     * 这里单独存一份，遥测层不会触碰。</p>
     *
     * <p>MDC 仍作为回退保留：异步线程上本绑定不存在时，行为与之前一致，不会引入回归。</p>
     */
    private static final ThreadLocal<String> APPLICATION_TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    /** 绑定本次请求的应用层 trace ID，应在请求进入时调用一次。 */
    public static void bind(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            APPLICATION_TRACE_ID.remove();
            return;
        }
        APPLICATION_TRACE_ID.set(traceId);
    }

    /** 解除当前线程的绑定，应在请求结束时调用，避免线程复用造成串号。 */
    public static void clear() {
        APPLICATION_TRACE_ID.remove();
    }

    public static String resolve(String candidate) {
        if (candidate != null) {
            String normalized = candidate.trim();
            if (VALID_TRACE_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return generate();
    }

    public static String currentOrCreate() {
        String bound = APPLICATION_TRACE_ID.get();
        if (bound != null) {
            return bound;
        }
        return resolve(MDC.get(MDC_KEY));
    }

    public static String explicitOrCurrent(String explicit) {
        if (explicit != null && VALID_TRACE_ID.matcher(explicit.trim()).matches()) {
            return explicit.trim();
        }
        String bound = APPLICATION_TRACE_ID.get();
        if (bound != null) {
            return bound;
        }
        String current = MDC.get(MDC_KEY);
        return current == null ? generate() : resolve(current);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
