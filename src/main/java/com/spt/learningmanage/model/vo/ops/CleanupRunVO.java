package com.spt.learningmanage.model.vo.ops;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CleanupRunVO {
    private String runId;
    private String clientRequestId;
    private String triggerType;
    private String policyVersion;
    private Boolean dryRun;
    private String status;
    private Long scannedCount;
    private Long estimatedCount;
    private Long affectedCount;
    private Long failureCount;
    private String errorSummary;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private Boolean idempotentReplay;
    private List<CleanupItemVO> items;
}
