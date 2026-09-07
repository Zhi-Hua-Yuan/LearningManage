package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage7ConfigurationValidatorTest {
    @Test
    void acceptsFrozenDefaults() {
        assertDoesNotThrow(() -> new Stage7ConfigurationValidator(
                new DataCleanupProperties(), new AiOpsProperties()).run(null));
    }

    @Test
    void scheduleCannotBypassCleanupFeatureFlag() {
        DataCleanupProperties cleanup = new DataCleanupProperties();
        cleanup.setScheduleEnabled(true);
        assertThrows(IllegalStateException.class,
                () -> new Stage7ConfigurationValidator(cleanup, new AiOpsProperties()).run(null));
    }

    @Test
    void costBudgetsMustBeCompleteAndOrdered() {
        AiOpsProperties ops = new AiOpsProperties();
        ops.setDailyCostSoftLimit(new BigDecimal("100"));
        ops.setDailyCostHardLimit(new BigDecimal("50"));
        assertThrows(IllegalStateException.class,
                () -> new Stage7ConfigurationValidator(new DataCleanupProperties(), ops).run(null));
    }
}
