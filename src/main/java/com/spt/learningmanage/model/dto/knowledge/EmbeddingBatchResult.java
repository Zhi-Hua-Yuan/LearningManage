package com.spt.learningmanage.model.dto.knowledge;

import java.util.List;

public record EmbeddingBatchResult(
        List<List<Float>> vectors,
        String actualModel,
        Long promptTokens,
        Long totalTokens,
        String providerRequestId
) {
    public EmbeddingBatchResult {
        vectors = vectors == null ? List.of() : vectors.stream().map(List::copyOf).toList();
    }
}
