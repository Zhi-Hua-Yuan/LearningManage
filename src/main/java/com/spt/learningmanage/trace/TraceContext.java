package com.spt.learningmanage.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private TraceContext() {
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
        return resolve(MDC.get(MDC_KEY));
    }

    public static String explicitOrCurrent(String explicit) {
        if (explicit != null && VALID_TRACE_ID.matcher(explicit.trim()).matches()) {
            return explicit.trim();
        }
        String current = MDC.get(MDC_KEY);
        return current == null ? generate() : resolve(current);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
