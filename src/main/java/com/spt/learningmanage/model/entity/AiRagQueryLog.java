package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_rag_query_log")
public class AiRagQueryLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String requestId;
    private Long userId;
    private Long projectId;
    private String questionHmac;
    private String status;
    private String retrievalConfigVersion;
    private String embeddingModel;
    private Integer embeddingDimension;
    private String rerankModel;
    private Integer initialTopK;
    private Integer finalTopK;
    private BigDecimal vectorThreshold;
    private BigDecimal rerankThreshold;
    private Integer candidateCount;
    private Integer authorizedCount;
    private Integer finalCount;
    private Integer degraded;
    private String failureType;
    private Long durationMs;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
