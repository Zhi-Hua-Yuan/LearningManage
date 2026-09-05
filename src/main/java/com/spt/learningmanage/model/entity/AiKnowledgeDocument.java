package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_document")
public class AiKnowledgeDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String documentKey;
    private String sourceType;
    private Long sourceId;
    private Long projectId;
    private Long teamId;
    private Long ownerUserId;
    private String visibilityType;
    private String contentHash;
    private String payloadHash;
    private String indexedContentHash;
    private String indexedPayloadHash;
    private String normalizerVersion;
    private String chunkingVersion;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Integer chunkCount;
    private String status;
    private String skipReason;
    private String workerToken;
    private Long lastEventId;
    private String lastError;
    private LocalDateTime indexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
