package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class AiGovernanceConfigurationValidator {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3,8}");
    private final AiProperties properties;

    public AiGovernanceConfigurationValidator(AiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        validateProviderConfiguration();
        requireRange("ai.connect-timeout-ms", properties.getConnectTimeoutMs(), 1000, 30000);
        requireRange("ai.read-timeout-ms", properties.getReadTimeoutMs(), 5000, 300000);

        AiProperties.Logging logging = properties.getLogging();
        requireRange("ai.logging.max-body-chars", logging.getMaxBodyChars(), 1, 100000);
        requireRange("ai.logging.max-error-chars", logging.getMaxErrorChars(), 1, 20000);

        AiProperties.Resilience resilience = properties.getResilience();
        requireRange("ai.resilience.max-concurrent-calls", resilience.getMaxConcurrentCalls(), 1, 200);
        requireRange("ai.resilience.max-wait-millis", resilience.getMaxWaitMillis(), 0, 30000);
        requireRange("ai.resilience.total-timeout-ms", resilience.getTotalTimeoutMs(), 5000, 600000);
        requireRange("ai.resilience.sliding-window-size", resilience.getSlidingWindowSize(), 2, 1000);
        requireRange("ai.resilience.minimum-number-of-calls", resilience.getMinimumNumberOfCalls(), 1,
                resilience.getSlidingWindowSize());
        requirePercent("ai.resilience.failure-rate-threshold", resilience.getFailureRateThreshold());
        requireRange("ai.resilience.slow-call-duration-ms", resilience.getSlowCallDurationMs(), 1, 600000);
        requirePercent("ai.resilience.slow-call-rate-threshold", resilience.getSlowCallRateThreshold());
        requireRange("ai.resilience.open-state-wait-ms", resilience.getOpenStateWaitMs(), 1, 600000);
        requireRange("ai.resilience.half-open-permitted-calls", resilience.getHalfOpenPermittedCalls(), 1, 100);

        validatePricing(properties.getPricing());
    }

    private void validateProviderConfiguration() {
        if (!properties.getChat().isEnabled()) {
            return;
        }
        requireText("ai.api-key", properties.getApiKey());
        requireText("ai.base-url", properties.getBaseUrl());
        requireText("ai.model", properties.getModel());
    }

    private void validatePricing(AiProperties.Pricing pricing) {
        boolean hasVersion = pricing.getVersion() != null && !pricing.getVersion().isBlank();
        String currency = pricing.getCurrency() == null ? "" : pricing.getCurrency().trim().toUpperCase();
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalStateException("ai.pricing.currency must be an uppercase currency code");
        }
        Map<String, AiProperties.ModelPrice> models = pricing.getModels();
        if (models == null || models.isEmpty()) {
            return;
        }
        boolean hasConfiguredPrice = models.values().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(price -> price.getInputPerMillion() != null || price.getOutputPerMillion() != null);
        if (hasConfiguredPrice && !hasVersion) {
            throw new IllegalStateException("ai.pricing.version must be configured when model prices exist");
        }
        for (Map.Entry<String, AiProperties.ModelPrice> entry : models.entrySet()) {
            AiProperties.ModelPrice price = entry.getValue();
            if (price == null || (price.getInputPerMillion() == null && price.getOutputPerMillion() == null)) {
                continue;
            }
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || price.getInputPerMillion() == null || price.getOutputPerMillion() == null) {
                throw new IllegalStateException("AI model pricing must contain both input and output prices");
            }
            requireNonNegative(price.getInputPerMillion());
            requireNonNegative(price.getOutputPerMillion());
        }
    }

    private void requireRange(String property, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new IllegalStateException(property + " must be between " + min + " and " + max);
        }
    }

    private void requirePercent(String property, float value) {
        if (value < 1.0f || value > 100.0f) {
            throw new IllegalStateException(property + " must be between 1 and 100");
        }
    }

    private void requireNonNegative(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalStateException("AI model prices cannot be negative");
        }
    }

    private void requireText(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured when ai.chat.enabled=true");
        }
    }
}
