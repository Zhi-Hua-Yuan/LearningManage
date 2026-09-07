package com.spt.learningmanage.observability;

import com.spt.learningmanage.ai.governance.AiCostEstimate;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMetricsRecorderTest {
    @Test
    void recordsOnlyLowCardinalityInvocationMetadata() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetricsRecorder recorder = new AiMetricsRecorder(registry);
        AiCallLogCompletionCommand command = new AiCallLogCompletionCommand(
                1L, AiCallLogStatusEnum.SUCCESS, null, null, 120L,
                "qwen-plus", "qwen-plus", 0, "stop", new AiUsage(10, 5, 15),
                "provider-secret-id", false, null, "user-specific-trace", null,
                false, null);

        recorder.recordInvocation("project-risk-report", command,
                new AiCostEstimate("price-v1", "CNY", new BigDecimal("0.001")));

        assertEquals(1D, registry.get("learning.ai.invocations").counter().count());
        assertEquals(15D, registry.get("learning.ai.tokens").counter().count());
        assertNull(registry.find("learning.ai.invocations").tag("traceId", "user-specific-trace").counter());
        assertNull(registry.find("learning.ai.invocations").tag("providerRequestId", "provider-secret-id").counter());
    }

    @Test
    void everyMetricTagUsesTheStage7AllowList() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetricsRecorder recorder = new AiMetricsRecorder(registry);
        AiCallLogCompletionCommand command = new AiCallLogCompletionCommand(
                1L, AiCallLogStatusEnum.SUCCESS, null, null, 120L,
                "qwen-plus", "qwen-plus", 0, "stop", new AiUsage(10, 5, 15),
                null, false, null, null, null, false, null);

        recorder.recordInvocation("project-risk-report", command,
                new AiCostEstimate("price-v1", "CNY", new BigDecimal("0.001")));
        recorder.recordRag("SUCCESS", false, false, 80, 5);
        recorder.recordAgentRun("project-risk", "SUCCEEDED", "TOOL_CALLING", 200);
        recorder.recordTool("queryProjectTasks", "SUCCEEDED", 20);
        recorder.recordKnowledgeEvent("task", "SUCCESS", "none", 30);
        recorder.recordCleanup("SUCCEEDED", 40, 10);

        Set<String> allowed = Set.of(
                "scene", "model", "status", "failure_type", "degraded",
                "orchestration_mode", "tool_name", "source_type",
                "currency", "price_version");
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag ->
                assertTrue(allowed.contains(tag.getKey()),
                        () -> "metric tag is not allow-listed: " + tag.getKey())));
    }
}
