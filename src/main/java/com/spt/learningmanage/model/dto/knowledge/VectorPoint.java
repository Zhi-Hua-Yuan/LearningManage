package com.spt.learningmanage.model.dto.knowledge;

import java.util.List;
import java.util.Map;

public record VectorPoint(String id, List<Float> vector, Map<String, Object> payload) {
    public VectorPoint {
        vector = List.copyOf(vector);
        payload = Map.copyOf(payload);
    }
}
