package com.spt.learningmanage.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class Stage7ConfigurationValidator implements ApplicationRunner {
    private final DataCleanupProperties cleanup;
    private final AiOpsProperties ops;

    public Stage7ConfigurationValidator(DataCleanupProperties cleanup, AiOpsProperties ops) {
        this.cleanup = cleanup;
        this.ops = ops;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateCleanup();
        validateBudgets();
    }

    private void validateCleanup() {
        if (cleanup.getPolicyVersion() == null || cleanup.getPolicyVersion().isBlank()
                || cleanup.getPolicyVersion().length() > 32) {
            throw new IllegalStateException("ai.cleanup.policy-version is invalid");
        }
        if (cleanup.getBatchSize() < 1 || cleanup.getBatchSize() > 1000
                || cleanup.getMaxRuntimeSeconds() < 10 || cleanup.getMaxRuntimeSeconds() > 3600
                || cleanup.getLeaseSeconds() < 10 || cleanup.getLeaseSeconds() > 600
                || cleanup.getDryRunValidHours() < 1 || cleanup.getDryRunValidHours() > 168
                || cleanup.getEstimateDriftRatio() < 0 || cleanup.getEstimateDriftRatio() > 1
                || cleanup.getEstimateDriftMinRows() < 0
                || cleanup.getBodyRetentionDays() < 1
                || cleanup.getMetadataRetentionDays() < cleanup.getBodyRetentionDays()
                || cleanup.getSuccessfulEventRetentionDays() < 1
                || cleanup.getAdminAuditRetentionDays() < cleanup.getMetadataRetentionDays()) {
            throw new IllegalStateException("ai.cleanup retention or worker configuration is invalid");
        }
        if (cleanup.isScheduleEnabled() && !cleanup.isEnabled()) {
            throw new IllegalStateException("scheduled cleanup requires ai.cleanup.enabled=true");
        }
    }

    private void validateBudgets() {
        BigDecimal soft = ops.getDailyCostSoftLimit();
        BigDecimal hard = ops.getDailyCostHardLimit();
        if (soft == null && hard == null) {
            return;
        }
        if (soft == null || hard == null || soft.signum() <= 0 || hard.signum() <= 0
                || soft.compareTo(hard) > 0) {
            throw new IllegalStateException("AI daily cost soft/hard limits must be positive and ordered");
        }
    }
}
