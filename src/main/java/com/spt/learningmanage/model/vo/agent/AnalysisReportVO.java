package com.spt.learningmanage.model.vo.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AnalysisReportVO(
        String reportId,
        String reportType,
        Long projectId,
        Long teamId,
        String status,
        String summary,
        Map<String, Object> memberMetrics,
        List<String> recommendations,
        List<ReportSourceVO> sources,
        LocalDateTime generatedAt
) {
}
