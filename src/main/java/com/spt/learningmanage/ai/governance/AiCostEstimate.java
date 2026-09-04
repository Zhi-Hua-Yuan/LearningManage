package com.spt.learningmanage.ai.governance;

import java.math.BigDecimal;

public record AiCostEstimate(
        String priceVersion,
        String currency,
        BigDecimal estimatedCost
) {

    public static AiCostEstimate unavailable() {
        return new AiCostEstimate(null, null, null);
    }
}
