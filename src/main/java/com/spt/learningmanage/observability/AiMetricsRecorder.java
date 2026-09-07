package com.spt.learningmanage.observability;

import com.spt.learningmanage.ai.governance.AiCostEstimate;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

/** Low-cardinality Stage 7 metrics. Never accepts IDs or content as tags. */
@Component
public class AiMetricsRecorder {
    private final MeterRegistry registry;

    public AiMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordInvocation(String scene,
                                 AiCallLogCompletionCommand command,
                                 AiCostEstimate cost) {
        String model = tag(command.actualModel());
        String status = tag(command.status().name());
        String failure = command.failureType() == null ? "none" : tag(command.failureType().name());
        String degraded = Boolean.toString(command.degraded());
        Counter.builder("learning.ai.invocations")
                .tags("scene", tag(scene), "model", model, "status", status,
                        "failure_type", failure, "degraded", degraded)
                .register(registry).increment();
        Timer.builder("learning.ai.invocation.duration")
                .tags("scene", tag(scene), "model", model, "status", status)
                .publishPercentileHistogram()
                .register(registry).record(Duration.ofMillis(command.costTimeMs()));
        if (command.usage() != null) {
            increment(command.usage().totalTokens(), scene, model);
        }
        if (cost != null && cost.estimatedCost() != null) {
            Counter.builder("learning.ai.estimated.cost")
                    .baseUnit(cost.currency())
                    .tags("scene", tag(scene), "model", model,
                            "currency", tag(cost.currency()), "price_version", tag(cost.priceVersion()))
                    .register(registry).increment(nonNegative(cost.estimatedCost()));
        }
    }

    public void recordRag(String status, boolean degraded, boolean insufficient,
                          long durationMs, int candidates) {
        Counter.builder("learning.rag.queries")
                .tags("status", tag(status), "degraded", Boolean.toString(degraded))
                .register(registry).increment();
        Timer.builder("learning.rag.query.duration")
                .tags("status", tag(status), "degraded", Boolean.toString(degraded))
                .publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
        DistributionSummary.builder("learning.rag.candidate.count")
                .tags("status", tag(status)).register(registry)
                .record(Math.max(candidates, 0));
        if (insufficient) {
            Counter.builder("learning.rag.insufficient")
                    .tags("status", tag(status)).register(registry).increment();
        }
    }

    public void recordAgentRun(String scene, String status, String mode, long durationMs) {
        Counter.builder("learning.agent.runs")
                .tags("scene", tag(scene), "status", tag(status),
                        "orchestration_mode", tag(mode))
                .register(registry).increment();
        Timer.builder("learning.agent.run.duration")
                .tags("scene", tag(scene), "status", tag(status),
                        "orchestration_mode", tag(mode))
                .publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
    }

    public void recordTool(String toolName, String status, long durationMs) {
        Counter.builder("learning.agent.tool.calls")
                .tags("tool_name", tag(toolName), "status", tag(status))
                .register(registry).increment();
        Timer.builder("learning.agent.tool.duration")
                .tags("tool_name", tag(toolName), "status", tag(status))
                .publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
    }

    public void recordKnowledgeEvent(String sourceType, String status,
                                     String failureType, long durationMs) {
        Counter.builder("learning.knowledge.events")
                .tags("source_type", tag(sourceType), "status", tag(status),
                        "failure_type", tag(failureType))
                .register(registry).increment();
        Timer.builder("learning.knowledge.index.duration")
                .tags("source_type", tag(sourceType), "status", tag(status))
                .publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
    }

    public void recordCleanup(String status, long durationMs, long affectedRows) {
        Counter.builder("learning.cleanup.runs")
                .tags("status", tag(status))
                .register(registry).increment();
        Counter.builder("learning.cleanup.rows")
                .tags("status", tag(status))
                .register(registry).increment(Math.max(affectedRows, 0));
        Timer.builder("learning.cleanup.duration")
                .tags("status", tag(status))
                .publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(Math.max(durationMs, 0)));
    }

    private void increment(Integer amount, String scene, String model) {
        if (amount == null || amount <= 0) {
            return;
        }
        Counter.builder("learning.ai.tokens")
                .baseUnit("tokens")
                .tags("scene", tag(scene), "model", model)
                .register(registry).increment(amount);
    }

    private double nonNegative(BigDecimal value) {
        return Math.max(value.doubleValue(), 0D);
    }

    private String tag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
