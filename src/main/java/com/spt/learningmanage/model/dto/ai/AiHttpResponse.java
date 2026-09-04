package com.spt.learningmanage.model.dto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record AiHttpResponse(
        int statusCode,
        String responseBody,
        Map<String, List<String>> headers
) {

    private static final List<String> SENSITIVE_HEADERS = List.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie"
    );

    public AiHttpResponse {
        TreeMap<String, List<String>> sanitized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (name == null || SENSITIVE_HEADERS.stream().anyMatch(name::equalsIgnoreCase)) {
                    return;
                }
                List<String> copiedValues = values == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(values));
                sanitized.put(name, copiedValues);
            });
        }
        headers = Collections.unmodifiableMap(sanitized);
    }

    public AiHttpResponse(int statusCode, String responseBody) {
        this(statusCode, responseBody, Map.of());
    }

    public String firstHeader(String name) {
        if (name == null) {
            return null;
        }
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
