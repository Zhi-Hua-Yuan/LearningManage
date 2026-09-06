package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_tool_log")
public class AiAgentToolLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String runId;
    private Integer attemptNo;
    private Integer toolSequence;
    private String toolCallId;
    private String toolName;
    private String status;
    private String argumentHash;
    private String argumentSummary;
    private String resultHash;
    private String resultSummary;
    private Long durationMs;
    private Long observedDataVersion;
    private String failureType;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
}
