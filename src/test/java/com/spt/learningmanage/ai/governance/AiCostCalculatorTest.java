package com.spt.learningmanage.ai.governance;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import com.spt.learningmanage.model.dto.ai.chat.AiAttemptSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiCostCalculatorTest {

    @Test
    void shouldCalculateConfiguredModelCostWithEightDecimals() {
        AiProperties properties = propertiesWithPrice();

        AiCostEstimate estimate = new AiCostCalculator(properties)
                .estimate("qwen-plus", new AiUsage(1_000, 500, 1_500));

        assertEquals("2026-09", estimate.priceVersion());
        assertEquals("CNY", estimate.currency());
        assertEquals(new BigDecimal("0.00600000"), estimate.estimatedCost());
    }

    @Test
    void shouldKeepCostUnknownWhenUsageOrPriceIsMissing() {
        AiProperties properties = propertiesWithPrice();
        AiCostCalculator calculator = new AiCostCalculator(properties);

        assertNull(calculator.estimate("unknown", new AiUsage(1, 1, 2)).estimatedCost());
        assertNull(calculator.estimate("qwen-plus", new AiUsage(null, 1, null)).estimatedCost());
    }

    @Test
    void shouldAggregateKnownUsageUsingEachAttemptModelPrice() {
        AiProperties properties = propertiesWithPrice();
        AiProperties.ModelPrice fallbackPrice = new AiProperties.ModelPrice();
        fallbackPrice.setInputPerMillion(new BigDecimal("1.00"));
        fallbackPrice.setOutputPerMillion(new BigDecimal("4.00"));
        properties.getPricing().getModels().put("qwen-flash", fallbackPrice);

        AiCostEstimate estimate = new AiCostCalculator(properties).estimate(List.of(
                new AiAttemptSummary("qwen-plus", new AiUsage(1_000, 500, 1_500), "p1", null, 10),
                new AiAttemptSummary("qwen-flash", new AiUsage(2_000, 1_000, 3_000), "p2", null, 20)
        ), "qwen-flash", new AiUsage(3_000, 1_500, 4_500));

        assertEquals(new BigDecimal("0.01200000"), estimate.estimatedCost());
    }

    private AiProperties propertiesWithPrice() {
        AiProperties properties = new AiProperties();
        properties.getPricing().setVersion("2026-09");
        properties.getPricing().setCurrency("cny");
        AiProperties.ModelPrice price = new AiProperties.ModelPrice();
        price.setInputPerMillion(new BigDecimal("2.00"));
        price.setOutputPerMillion(new BigDecimal("8.00"));
        properties.getPricing().getModels().put("qwen-plus", price);
        return properties;
    }
}
