package com.spt.learningmanage.model.dto.knowledge;

import java.util.List;

public record EmbeddingCallContext(
        Long ownerUserId,
        String traceId,
        List<String> contentHashes
) {
    public EmbeddingCallContext {
        contentHashes = contentHashes == null ? List.of() : List.copyOf(contentHashes);
    }
}
