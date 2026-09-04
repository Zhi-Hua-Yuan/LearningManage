package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AiCallLogStatsVO {

    private Long totalCount;

    private Long runningCount;

    private Long successCount;

    private Long failedCount;

    private Long parseFailedCount;

    private Long timeoutCount;

    private BigDecimal successRate;

    private Long avgCostTimeMs;

    private Long maxCostTimeMs;

    private Long minCostTimeMs;

    private Long totalPromptTokens;

    private Long totalCompletionTokens;

    private Long totalTokens;

    private BigDecimal totalEstimatedCost;

    private Long unknownUsageCount;

    private Long fallbackCount;

    private Long degradedCount;

    private List<AiCallLogSceneStatsVO> sceneStats;

    private List<AiCallLogStatusStatsVO> statusStats;
}
