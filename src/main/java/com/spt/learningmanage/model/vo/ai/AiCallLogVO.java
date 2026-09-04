package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiCallLogVO {

    private Long id;

    private String scene;

    private String modelName;

    private String requestedModel;

    private String actualModel;

    private String traceId;

    private String providerRequestId;

    private String promptType;

    private Long promptTemplateId;

    private Integer promptVersion;

    private String promptSource;

    private String requestPreview;

    private String responsePreview;

    private Integer status;

    private String statusText;

    private String errorMessage;

    private Long costTimeMs;

    private Integer retryCount;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private String priceVersion;

    private String currency;

    private BigDecimal estimatedCost;

    private Integer fallbackUsed;

    private Integer degraded;

    private String requestSanitizationStatus;

    private String responseSanitizationStatus;

    private String errorSanitizationStatus;

    private Integer requestTruncated;

    private Integer responseTruncated;

    private Integer errorTruncated;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
