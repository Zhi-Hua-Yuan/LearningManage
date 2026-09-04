package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AiCallLogDetailVO {

    private Long id;

    private Long userId;

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

    private String requestText;

    private Boolean requestTextTruncated;

    private String responseText;

    private Boolean responseTextTruncated;

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

    private String failureType;

    private String requestSanitizationStatus;

    private String responseSanitizationStatus;

    private String errorSanitizationStatus;

    private Integer requestTruncated;

    private Integer responseTruncated;

    private Integer errorTruncated;

    private String requestHash;

    private String responseHash;

    private String errorHash;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
