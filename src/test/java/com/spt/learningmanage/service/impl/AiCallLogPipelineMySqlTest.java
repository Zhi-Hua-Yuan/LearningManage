package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.service.AiCallLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AiCallLogPipelineMySqlTest {

    @Autowired
    private AiCallLogService aiCallLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeAnIsolatedV3Database() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "WP3 pipeline tests must use an isolated test database");
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1", Integer.class));
    }

    @Test
    void shouldPersistMainModelSuccessMetadata() {
        Long logId = createRunning("trace-main-success");

        assertTrue(aiCallLogService.complete(completion(
                logId, AiCallLogStatusEnum.SUCCESS, "requested-model", "requested-model",
                0, false, null, false, null, null
        )));

        Map<String, Object> row = row(logId);
        assertEquals(AiCallLogStatusEnum.SUCCESS.getValue(), number(row, "status").intValue());
        assertEquals("requested-model", row.get("requested_model"));
        assertEquals("requested-model", row.get("model_name"));
        assertEquals(12L, number(row, "prompt_tokens").longValue());
        assertEquals(8L, number(row, "completion_tokens").longValue());
        assertEquals(20L, number(row, "total_tokens").longValue());
        assertEquals("provider-wp3", row.get("provider_request_id"));
        assertEquals(0, number(row, "fallback_used").intValue());
        assertEquals(0, number(row, "degraded").intValue());
        assertNull(row.get("failure_type"));
    }

    @Test
    void shouldPersistSuccessfulModelFallbackIndependentlyFromRuleDegradation() {
        Long logId = createRunning("trace-model-fallback");

        assertTrue(aiCallLogService.complete(completion(
                logId, AiCallLogStatusEnum.SUCCESS, "requested-model", "fallback-model",
                1, true, AiFailureTypeEnum.NETWORK_ERROR, false, null, null
        )));

        Map<String, Object> row = row(logId);
        assertEquals("fallback-model", row.get("model_name"));
        assertEquals(1, number(row, "retry_count").intValue());
        assertEquals(1, number(row, "fallback_used").intValue());
        assertEquals(AiFailureTypeEnum.NETWORK_ERROR.name(), row.get("fallback_reason"));
        assertEquals(0, number(row, "degraded").intValue());
        assertNull(row.get("failure_type"));
    }

    @Test
    void shouldPersistRuleDegradationWithOriginalFailure() {
        Long logId = createRunning("trace-rule-degraded");
        String degradationReason = "TIMEOUT: AI 服务响应超时";

        assertTrue(aiCallLogService.complete(completion(
                logId, AiCallLogStatusEnum.SUCCESS, "requested-model", "requested-model",
                0, false, null, true, AiCallFailureTypeEnum.TIMEOUT, degradationReason
        )));

        Map<String, Object> row = row(logId);
        assertEquals(AiCallLogStatusEnum.SUCCESS.getValue(), number(row, "status").intValue());
        assertEquals(1, number(row, "degraded").intValue());
        assertEquals(0, number(row, "fallback_used").intValue());
        assertEquals(AiCallFailureTypeEnum.TIMEOUT.name(), row.get("failure_type"));
        assertEquals(degradationReason, row.get("error_message"));
    }

    @Test
    void shouldAllowOnlyOneTerminalStateTransition() {
        Long logId = createRunning("trace-terminal-cas");
        AiCallLogCompletionCommand timeout = completion(
                logId, AiCallLogStatusEnum.TIMEOUT, "requested-model", "requested-model",
                0, false, null, false, AiCallFailureTypeEnum.TIMEOUT, null
        );
        AiCallLogCompletionCommand laterSuccess = completion(
                logId, AiCallLogStatusEnum.SUCCESS, "requested-model", "requested-model",
                0, false, null, false, null, null
        );

        assertTrue(aiCallLogService.complete(timeout));
        assertFalse(aiCallLogService.complete(laterSuccess));

        Map<String, Object> row = row(logId);
        assertEquals(AiCallLogStatusEnum.TIMEOUT.getValue(), number(row, "status").intValue());
        assertEquals(AiCallFailureTypeEnum.TIMEOUT.name(), row.get("failure_type"));
    }

    private Long createRunning(String traceId) {
        return aiCallLogService.createRunningLog(new AiCallLogCreateCommand(
                99001L, "wp3-pipeline-test", "requested-model", "wp3-test",
                null, 1, "builtin", "{\"request\":\"wp3\"}", 0, traceId
        ));
    }

    private AiCallLogCompletionCommand completion(Long logId,
                                                   AiCallLogStatusEnum status,
                                                   String requestedModel,
                                                   String actualModel,
                                                   int retryCount,
                                                   boolean fallbackUsed,
                                                   AiFailureTypeEnum fallbackReason,
                                                   boolean degraded,
                                                   AiCallFailureTypeEnum failureType,
                                                   String degradationReason) {
        boolean success = status == AiCallLogStatusEnum.SUCCESS;
        return new AiCallLogCompletionCommand(
                logId, status, success ? "response" : null,
                degraded ? null : failureType == null ? null : "AI 调用失败",
                25L, requestedModel, actualModel, retryCount, success ? "stop" : null,
                success ? new AiUsage(12, 8, 20) : null, success ? "provider-wp3" : null, fallbackUsed,
                fallbackReason, "trace-completed", failureType, degraded, degradationReason
        );
    }

    private Map<String, Object> row(Long logId) {
        return jdbcTemplate.queryForMap("SELECT * FROM ai_call_log WHERE id = ?", logId);
    }

    private Number number(Map<String, Object> row, String column) {
        return (Number) row.get(column);
    }
}
