package com.spt.learningmanage.agent.model;

public record AgentReportSourcePayload(String citationId,
                                       String sourceType,
                                       Long sourceId,
                                       String documentKey,
                                       int chunkIndex,
                                       String contentHash,
                                       String payloadHash,
                                       String title) {
}
