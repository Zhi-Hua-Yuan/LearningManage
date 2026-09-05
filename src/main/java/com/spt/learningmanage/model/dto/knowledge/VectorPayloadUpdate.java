package com.spt.learningmanage.model.dto.knowledge;

import java.util.Map;

public record VectorPayloadUpdate(String pointId, Map<String, Object> payload) {
    public VectorPayloadUpdate {
        payload = Map.copyOf(payload);
    }
}
