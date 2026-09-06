package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_result")
public class AiRagResult {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String requestId;
    private Long queryLogId;
    private Long userId;
    private Long projectId;
    private String answerText;
    private String answerHash;
    private String status;
    private Integer insufficientEvidence;
    private Integer degraded;
    private String degradationReason;
    private Long aiCallLogId;
    private String model;
    private String promptCode;
    private Integer promptVersion;
    private String retrievalConfigVersion;
    private LocalDateTime knowledgeAsOf;
    private String traceId;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
