package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_backfill_run")
public class AiKnowledgeBackfillRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runKey;
    private String runType;
    private String sourceScope;
    private String status;
    private Long cursorTaskId;
    private Long cursorReviewId;
    private Long discoveredCount;
    private Long enqueuedCount;
    private Long successCount;
    private Long failedCount;
    private Long deadCount;
    private String workerId;
    private LocalDateTime leaseUntil;
    private String traceId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
