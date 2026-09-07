package com.spt.learningmanage.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.spt.learningmanage.service.AiCallLogOperationsService;
import com.spt.learningmanage.config.AiOpsProperties;
import java.math.BigDecimal;

@Component
public class OperationalGaugeBinder {
    private final AiAgentRunMapper agentRunMapper;
    private final AiKnowledgeIndexEventMapper eventMapper;
    private final AiCallLogOperationsService callLogOperations;
    private final AiOpsProperties opsProperties;
    private final AtomicLong agentDepth = new AtomicLong();
    private final AtomicLong agentOldest = new AtomicLong();
    private final AtomicLong knowledgeDepth = new AtomicLong();
    private final AtomicLong knowledgeOldest = new AtomicLong();
    private final AtomicLong knowledgeDead = new AtomicLong();
    private final AtomicReference<Double> dailyCost = new AtomicReference<>(0D);
    private final AtomicReference<Double> softBudget = new AtomicReference<>(Double.NaN);
    private final AtomicReference<Double> hardBudget = new AtomicReference<>(Double.NaN);

    public OperationalGaugeBinder(MeterRegistry registry,
                                  AiAgentRunMapper agentRunMapper,
                                  AiKnowledgeIndexEventMapper eventMapper,
                                  AiCallLogOperationsService callLogOperations,
                                  AiOpsProperties opsProperties) {
        this.agentRunMapper = agentRunMapper;
        this.eventMapper = eventMapper;
        this.callLogOperations = callLogOperations;
        this.opsProperties = opsProperties;
        Gauge.builder("learning.agent.queue.depth", agentDepth, AtomicLong::get).register(registry);
        Gauge.builder("learning.agent.oldest.pending", agentOldest, AtomicLong::get)
                .baseUnit("seconds").register(registry);
        Gauge.builder("learning.knowledge.queue.depth", knowledgeDepth, AtomicLong::get).register(registry);
        Gauge.builder("learning.knowledge.oldest.pending", knowledgeOldest, AtomicLong::get)
                .baseUnit("seconds").register(registry);
        Gauge.builder("learning.knowledge.dead", knowledgeDead, AtomicLong::get).register(registry);
        Gauge.builder("learning.ai.daily.estimated.cost", dailyCost, AtomicReference::get).register(registry);
        Gauge.builder("learning.ai.daily.cost.soft.budget", softBudget, AtomicReference::get).register(registry);
        Gauge.builder("learning.ai.daily.cost.hard.budget", hardBudget, AtomicReference::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${management.metrics.queue-refresh-ms:15000}")
    public void refresh() {
        try {
            agentDepth.set(agentRunMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>()
                    .eq(AiAgentRun::getStatus, "PENDING")));
            agentOldest.set(ageSeconds(agentRunMapper.selectOne(new LambdaQueryWrapper<AiAgentRun>()
                    .eq(AiAgentRun::getStatus, "PENDING")
                    .orderByAsc(AiAgentRun::getCreateTime).last("limit 1"))));
            knowledgeDepth.set(eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .in(AiKnowledgeIndexEvent::getStatus, "PENDING", "RETRY_WAIT")));
            knowledgeOldest.set(ageSeconds(eventMapper.selectOne(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .in(AiKnowledgeIndexEvent::getStatus, "PENDING", "RETRY_WAIT")
                    .orderByAsc(AiKnowledgeIndexEvent::getCreateTime).last("limit 1"))));
            knowledgeDead.set(eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getStatus, "DEAD")));
            LocalDateTime now = LocalDateTime.now();
            BigDecimal cost = callLogOperations.sumEstimatedCost(now.toLocalDate().atStartOfDay(), now);
            dailyCost.set(cost == null ? Double.NaN : cost.doubleValue());
            softBudget.set(value(opsProperties.getDailyCostSoftLimit()));
            hardBudget.set(value(opsProperties.getDailyCostHardLimit()));
        } catch (RuntimeException ignored) {
            // Scrape must never make the application unavailable. Last good values remain visible.
        }
    }

    private double value(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private long ageSeconds(AiAgentRun run) {
        return run == null || run.getCreateTime() == null ? 0L
                : Math.max(Duration.between(run.getCreateTime(), LocalDateTime.now()).toSeconds(), 0L);
    }

    private long ageSeconds(AiKnowledgeIndexEvent event) {
        return event == null || event.getCreateTime() == null ? 0L
                : Math.max(Duration.between(event.getCreateTime(), LocalDateTime.now()).toSeconds(), 0L);
    }
}
