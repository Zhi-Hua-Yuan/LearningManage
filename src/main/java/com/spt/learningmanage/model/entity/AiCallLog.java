package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String scene;

    private String modelName;

    private String requestedModel;

    private String finishReason;

    private String providerRequestId;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private String priceVersion;

    private String currency;

    private BigDecimal estimatedCost;

    private String traceId;

    private String failureType;

    private Integer fallbackUsed;

    private String fallbackReason;

    private Integer degraded;

    private String requestSanitizationStatus;

    private String responseSanitizationStatus;

    private String errorSanitizationStatus;

    private Integer requestTruncated;

    private Integer responseTruncated;

    private Integer errorTruncated;

    private String requestHash;

    private String responseHash;

    private String errorHash;

    private LocalDateTime bodyPurgedAt;

    private String promptType;

    private Long promptTemplateId;

    private Integer promptVersion;

    private String promptSource;

    private String requestText;

    private String responseText;

    private Integer status;

    private String errorMessage;

    private Long costTimeMs;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
