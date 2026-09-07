package com.spt.learningmanage.model.vo.ops;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AiOpsSummaryVO {
    private LocalDateTime from;
    private LocalDateTime to;
    private Long totalCount;
    private Map<String, Long> statusCounts;
    private Map<String, Long> dimensionCounts;
    private Long p50DurationMs;
    private Long p95DurationMs;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    private String currency;
    private Map<String, Long> queueCounts;
}
