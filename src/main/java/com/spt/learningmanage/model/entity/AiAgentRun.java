package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_run")
public class AiAgentRun {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String runId;
    private String clientRequestId;
    private String scene;
    private Long userId;
    private Long projectId;
    private Long teamId;
    private String status;
    private String orchestrationMode;
    private String currentStep;
    private Integer toolCount;
    private Integer attemptCount;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatAt;
    private LocalDateTime cancelRequestedAt;
    private String executionToken;
    private Long startDataVersion;
    private Long endDataVersion;
    private String draftId;
    private Long aiCallLogId;
    private String partialReason;
    private String failureType;
    private String errorSummary;
    private String model;
    private String promptCode;
    private Integer promptVersion;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
