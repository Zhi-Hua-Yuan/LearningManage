package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiCallLogSceneStatsVO {

    private String scene;

    private Long totalCount;

    private Long runningCount;

    private Long successCount;

    private Long failedCount;

    private Long parseFailedCount;

    private Long timeoutCount;

    private BigDecimal successRate;

    private Long avgCostTimeMs;
}
