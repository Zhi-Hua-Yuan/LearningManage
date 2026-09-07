package com.spt.learningmanage.model.vo.ops;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CleanupItemVO {
    private String resourceType;
    private LocalDateTime cutoffTime;
    private String status;
    private Long cursorId;
    private Long scannedCount;
    private Long estimatedCount;
    private Long redactedCount;
    private Long deletedCount;
    private String errorSummary;
}
