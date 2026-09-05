package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_index_event")
public class AiKnowledgeIndexEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String eventType;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String claimedBy;
    private String claimToken;
    private LocalDateTime claimedAt;
    private LocalDateTime leaseUntil;
    private Long backfillRunId;
    private String failureType;
    private String lastError;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
