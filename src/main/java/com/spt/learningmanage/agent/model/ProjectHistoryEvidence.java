package com.spt.learningmanage.agent.model;

import java.time.LocalDateTime;

public record ProjectHistoryEvidence(
        String citationId,
        String sourceType,
        Long sourceId,
        String documentKey,
        int chunkIndex,
        String contentHash,
        String payloadHash,
        String title,
        String text,
        double score,
        LocalDateTime updatedAt
) {
}
