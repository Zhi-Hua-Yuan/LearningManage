package com.spt.learningmanage.model.rag;

import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;

import java.time.LocalDateTime;

public record RagCandidate(
        String candidateId,
        String pointId,
        String documentKey,
        KnowledgeSourceTypeEnum sourceType,
        Long sourceId,
        int chunkIndex,
        String title,
        String text,
        String contentHash,
        String payloadHash,
        double vectorScore,
        Double rerankScore,
        LocalDateTime sourceUpdatedAt
) {
    public RagCandidate withRerankScore(double score) {
        return new RagCandidate(candidateId, pointId, documentKey, sourceType, sourceId,
                chunkIndex, title, text, contentHash, payloadHash, vectorScore, score,
                sourceUpdatedAt);
    }

    public double finalScore() {
        return rerankScore == null ? vectorScore : rerankScore;
    }
}
