package com.spt.learningmanage.agent.model;

import java.time.LocalDateTime;
import java.util.List;

public record AgentReportDraftPayload(
        String sourceRunId,
        String reportType,
        int schemaVersion,
        Long projectId,
        Long teamId,
        long sourceDataVersion,
        String riskLevel,
        String managerSummary,
        String publicSummary,
        List<TeamMemberMetricSnapshot> memberMetrics,
        List<String> recommendations,
        List<AgentRiskItem> riskItems,
        List<String> positiveSignals,
        List<AgentReportSourcePayload> sources,
        String model,
        String promptCode,
        Integer promptVersion,
        String traceId,
        LocalDateTime generatedAt
) {
    public AgentReportDraftPayload {
        memberMetrics = memberMetrics == null ? List.of() : List.copyOf(memberMetrics);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
        positiveSignals = positiveSignals == null ? List.of() : List.copyOf(positiveSignals);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}

