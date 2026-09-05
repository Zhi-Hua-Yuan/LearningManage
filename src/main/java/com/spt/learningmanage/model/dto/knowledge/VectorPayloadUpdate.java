package com.spt.learningmanage.model.dto.knowledge;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record VectorPayloadUpdate(String pointId, Map<String, Object> payload) {
    public VectorPayloadUpdate {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
