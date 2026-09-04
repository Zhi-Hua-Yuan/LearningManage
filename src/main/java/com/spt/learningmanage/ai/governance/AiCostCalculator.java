package com.spt.learningmanage.ai.governance;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.model.dto.ai.chat.AiAttemptSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class AiCostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private final AiProperties aiProperties;

    public AiCostCalculator(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AiCostEstimate estimate(String actualModel, AiUsage usage) {
        AiProperties.Pricing pricing = aiProperties.getPricing();
        if (usage == null || usage.promptTokens() == null || usage.completionTokens() == null
                || actualModel == null || pricing.getVersion() == null || pricing.getVersion().isBlank()) {
            return AiCostEstimate.unavailable();
        }
        AiProperties.ModelPrice modelPrice = pricing.getModels().get(actualModel.trim());
        if (modelPrice == null || modelPrice.getInputPerMillion() == null
                || modelPrice.getOutputPerMillion() == null) {
            return AiCostEstimate.unavailable();
        }
        BigDecimal inputCost = modelPrice.getInputPerMillion()
                .multiply(BigDecimal.valueOf(Math.max(usage.promptTokens(), 0L)))
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCost = modelPrice.getOutputPerMillion()
                .multiply(BigDecimal.valueOf(Math.max(usage.completionTokens(), 0L)))
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return new AiCostEstimate(
                pricing.getVersion().trim(),
                pricing.getCurrency().trim().toUpperCase(),
                inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP)
        );
    }

    public AiCostEstimate estimate(List<AiAttemptSummary> attempts,
                                   String actualModel,
                                   AiUsage aggregateUsage) {
        if (attempts == null || attempts.isEmpty()) {
            return estimate(actualModel, aggregateUsage);
        }
        AiProperties.Pricing pricing = aiProperties.getPricing();
        if (pricing.getVersion() == null || pricing.getVersion().isBlank()) {
            return AiCostEstimate.unavailable();
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean foundKnownUsage = false;
        for (AiAttemptSummary attempt : attempts) {
            if (attempt.usage() == null || attempt.usage().promptTokens() == null
                    || attempt.usage().completionTokens() == null) {
                continue;
            }
            foundKnownUsage = true;
            AiCostEstimate attemptCost = estimate(attempt.model(), attempt.usage());
            if (attemptCost.estimatedCost() == null) {
                return AiCostEstimate.unavailable();
            }
            total = total.add(attemptCost.estimatedCost());
        }
        if (!foundKnownUsage) {
            return AiCostEstimate.unavailable();
        }
        return new AiCostEstimate(pricing.getVersion().trim(),
                pricing.getCurrency().trim().toUpperCase(), total.setScale(8, RoundingMode.HALF_UP));
    }
}
