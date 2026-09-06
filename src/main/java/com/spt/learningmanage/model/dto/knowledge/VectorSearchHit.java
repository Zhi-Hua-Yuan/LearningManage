package com.spt.learningmanage.model.dto.knowledge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record VectorSearchHit(
        String pointId,
        double score,
        Map<String, Object> payload
) {
    public VectorSearchHit {
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
