package com.spt.learningmanage.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBackfillVO {
    private Long runId;
    private String runKey;
    private String runType;
    private String sourceScope;
    private Integer batchSize;
    private String status;
    private Long discoveredCount;
    private Long enqueuedCount;
    private Long successCount;
    private Long failedCount;
    private Long deadCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private boolean idempotentReplay;
}
