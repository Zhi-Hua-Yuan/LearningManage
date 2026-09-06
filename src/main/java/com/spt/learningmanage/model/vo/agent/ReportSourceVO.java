package com.spt.learningmanage.model.vo.agent;

public record ReportSourceVO(
        String citationId,
        String sourceType,
        Long sourceId,
        String title
) {
}
