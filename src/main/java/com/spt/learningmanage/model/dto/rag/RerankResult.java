package com.spt.learningmanage.model.dto.rag;

import java.util.List;

public record RerankResult(
        List<RerankItem> items,
        String actualModel,
        Long inputTokens,
        String providerRequestId
) {
    public RerankResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
