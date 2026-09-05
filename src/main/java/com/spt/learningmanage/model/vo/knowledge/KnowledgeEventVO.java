package com.spt.learningmanage.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeEventVO {
    private Long eventId;
    private String sourceType;
    private Long sourceId;
    private String eventType;
    private String status;
    private Integer attemptCount;
    private String failureType;
    private String lastError;
    private String traceId;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
