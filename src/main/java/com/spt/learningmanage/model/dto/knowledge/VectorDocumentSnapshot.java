package com.spt.learningmanage.model.dto.knowledge;

import java.util.List;

public record VectorDocumentSnapshot(List<VectorPointMetadata> points) {
    public VectorDocumentSnapshot {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
