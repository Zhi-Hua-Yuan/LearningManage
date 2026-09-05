package com.spt.learningmanage.model.dto.knowledge;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record VectorPointMetadata(String id, Map<String, Object> payload) {
    public VectorPointMetadata {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
