package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_data_cleanup_run")
public class AiDataCleanupRun {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String runId;
    private String clientRequestId;
    private Long initiatorUserId;
    private String triggerType;
    private String policyVersion;
    private String resourceHash;
    private Long approvedDryRunId;
    private Integer dryRun;
    private String status;
    private String workerId;
    private String executionToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime cancelRequestedAt;
    private Long scannedCount;
    private Long estimatedCount;
    private Long affectedCount;
    private Long failureCount;
    private String errorSummary;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
