package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_source_lock")
public class AiKnowledgeSourceLock {

    private String sourceType;
    private Long sourceId;
    private String ownerToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime updateTime;
}
