package com.spt.learningmanage.model.dto.knowledge;

import java.util.Map;

public record VectorPointMetadata(String id, Map<String, Object> payload) {
    public VectorPointMetadata {
        payload = Map.copyOf(payload);
    }
}
