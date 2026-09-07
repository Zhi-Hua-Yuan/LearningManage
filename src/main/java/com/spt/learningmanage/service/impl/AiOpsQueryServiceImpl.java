package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.*;
import com.spt.learningmanage.model.entity.*;
import com.spt.learningmanage.model.vo.ops.*;
import com.spt.learningmanage.service.AiDependencyHealthService;
import com.spt.learningmanage.service.AiOpsQueryService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiOpsQueryServiceImpl implements AiOpsQueryService {
    private final com.spt.learningmanage.service.AiCallLogOperationsService callOperations;
    private final AiRagQueryLogMapper ragMapper;
    private final AiAgentRunMapper agentMapper;
    private final AiKnowledgeIndexEventMapper eventMapper;
    private final AiDataCleanupRunMapper cleanupMapper;
    private final AiDependencyHealthService dependencyHealth;
    private final PermissionService permissionService;

    public AiOpsQueryServiceImpl(com.spt.learningmanage.service.AiCallLogOperationsService callOperations,
                                 AiRagQueryLogMapper ragMapper,
                                 AiAgentRunMapper agentMapper,
                                 AiKnowledgeIndexEventMapper eventMapper,
                                 AiDataCleanupRunMapper cleanupMapper,
                                 AiDependencyHealthService dependencyHealth,
                                 PermissionService permissionService) {
        this.callOperations = callOperations;
        this.ragMapper = ragMapper;
        this.agentMapper = agentMapper;
        this.eventMapper = eventMapper;
        this.cleanupMapper = cleanupMapper;
        this.dependencyHealth = dependencyHealth;
        this.permissionService = permissionService;
    }

    @Override
    public AiOpsOverviewVO overview(LocalDateTime from, LocalDateTime to) {
        requireAdmin();
        Range range = range(from, to);
        AiOpsOverviewVO result = new AiOpsOverviewVO();
        result.setFrom(range.from());
        result.setTo(range.to());
        result.setAi(ai(range));
        result.setRag(rag(range));
        result.setAgent(agent(range));
        result.setKnowledgeQueue(knowledgeQueue());
        result.setDependencies(dependencyHealth.snapshot());
        return result;
    }

    @Override
    public AiOpsSummaryVO rag(LocalDateTime from, LocalDateTime to) {
        requireAdmin();
        return rag(range(from, to));
    }

    @Override
    public AiOpsSummaryVO agent(LocalDateTime from, LocalDateTime to) {
        requireAdmin();
        return agent(range(from, to));
    }

    @Override
    public Page<OpsFailureVO> failures(LocalDateTime from, LocalDateTime to, long current, long size) {
        requireAdmin();
        Range range = range(from, to);
        long page = Math.max(current, 1);
        long pageSize = Math.max(1, Math.min(size, 100));
        List<OpsFailureVO> values = new ArrayList<>();
        callOperations.listFailureMetadata(range.from(), range.to(), 500)
                .forEach(row -> values.add(failure("AI", status(row.getStatus()), row.getFailureType(),
                        row.getTraceId(), row.getUpdateTime())));
        ragMapper.selectList(ragRange(range).eq(AiRagQueryLog::getStatus, "FAILED")
                        .select(AiRagQueryLog::getStatus, AiRagQueryLog::getFailureType,
                                AiRagQueryLog::getTraceId, AiRagQueryLog::getUpdateTime)
                        .orderByDesc(AiRagQueryLog::getUpdateTime).last("limit 500"))
                .forEach(row -> values.add(failure("RAG", row.getStatus(), row.getFailureType(),
                        row.getTraceId(), row.getUpdateTime())));
        agentMapper.selectList(agentRange(range).in(AiAgentRun::getStatus,
                                "FAILED", "TIMED_OUT", "PARTIAL")
                        .select(AiAgentRun::getStatus, AiAgentRun::getFailureType,
                                AiAgentRun::getTraceId, AiAgentRun::getUpdateTime)
                        .orderByDesc(AiAgentRun::getUpdateTime).last("limit 500"))
                .forEach(row -> values.add(failure("AGENT", row.getStatus(), row.getFailureType(),
                        row.getTraceId(), row.getUpdateTime())));
        eventMapper.selectList(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                        .ge(AiKnowledgeIndexEvent::getCreateTime, range.from())
                        .le(AiKnowledgeIndexEvent::getCreateTime, range.to())
                        .eq(AiKnowledgeIndexEvent::getStatus, "DEAD")
                        .select(AiKnowledgeIndexEvent::getStatus, AiKnowledgeIndexEvent::getFailureType,
                                AiKnowledgeIndexEvent::getTraceId, AiKnowledgeIndexEvent::getUpdateTime)
                        .orderByDesc(AiKnowledgeIndexEvent::getUpdateTime).last("limit 500"))
                .forEach(row -> values.add(failure("KNOWLEDGE", row.getStatus(), row.getFailureType(),
                        row.getTraceId(), row.getUpdateTime())));
        cleanupMapper.selectList(new LambdaQueryWrapper<AiDataCleanupRun>()
                        .ge(AiDataCleanupRun::getCreateTime, range.from())
                        .le(AiDataCleanupRun::getCreateTime, range.to())
                        .in(AiDataCleanupRun::getStatus, "FAILED", "PARTIAL")
                        .select(AiDataCleanupRun::getStatus, AiDataCleanupRun::getTraceId,
                                AiDataCleanupRun::getUpdateTime)
                        .orderByDesc(AiDataCleanupRun::getUpdateTime).last("limit 500"))
                .forEach(row -> values.add(failure("CLEANUP", row.getStatus(), row.getStatus(),
                        row.getTraceId(), row.getUpdateTime())));
        values.sort(Comparator.comparing(OpsFailureVO::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        long total = values.size();
        int start = (int) Math.min((page - 1) * pageSize, total);
        int end = (int) Math.min(start + pageSize, total);
        Page<OpsFailureVO> result = new Page<>(page, pageSize, total);
        result.setRecords(values.subList(start, end));
        return result;
    }

    @Override
    public Map<String, DependencyStatusVO> dependencies() {
        requireAdmin();
        dependencyHealth.refresh();
        return dependencyHealth.snapshot();
    }

    private AiOpsSummaryVO ai(Range range) {
        List<AiCallLog> rows = callOperations.listMetadata(range.from(), range.to());
        AiOpsSummaryVO result = base(range, rows.size(),
                counts(rows, row -> status(row.getStatus())),
                counts(rows, AiCallLog::getScene),
                rows.stream().map(AiCallLog::getCostTimeMs).filter(Objects::nonNull).toList());
        result.setTotalTokens(rows.stream().map(AiCallLog::getTotalTokens).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum());
        List<BigDecimal> knownCosts = rows.stream().map(AiCallLog::getEstimatedCost)
                .filter(Objects::nonNull).toList();
        result.setEstimatedCost(knownCosts.isEmpty() ? null
                : knownCosts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setCurrency(knownCosts.isEmpty() ? null
                : rows.stream().map(AiCallLog::getCurrency).filter(Objects::nonNull)
                        .findFirst().orElse(null));
        return result;
    }

    private AiOpsSummaryVO rag(Range range) {
        List<AiRagQueryLog> rows = ragMapper.selectList(ragRange(range)
                .select(AiRagQueryLog::getStatus, AiRagQueryLog::getDegraded,
                        AiRagQueryLog::getDurationMs, AiRagQueryLog::getFinalCount));
        return base(range, rows.size(), counts(rows, AiRagQueryLog::getStatus),
                counts(rows, row -> Objects.equals(row.getDegraded(), 1) ? "degraded" : "normal"),
                rows.stream().map(AiRagQueryLog::getDurationMs).filter(Objects::nonNull).toList());
    }

    private AiOpsSummaryVO agent(Range range) {
        List<AiAgentRun> rows = agentMapper.selectList(agentRange(range)
                .select(AiAgentRun::getStatus, AiAgentRun::getOrchestrationMode,
                        AiAgentRun::getStartedAt, AiAgentRun::getFinishedAt));
        List<Long> durations = rows.stream().filter(row -> row.getStartedAt() != null && row.getFinishedAt() != null)
                .map(row -> Math.max(java.time.Duration.between(row.getStartedAt(), row.getFinishedAt()).toMillis(), 0))
                .toList();
        AiOpsSummaryVO result = base(range, rows.size(), counts(rows, AiAgentRun::getStatus),
                counts(rows, AiAgentRun::getOrchestrationMode), durations);
        result.setQueueCounts(Map.of(
                "PENDING", agentMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>().eq(AiAgentRun::getStatus, "PENDING")),
                "RUNNING", agentMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>().eq(AiAgentRun::getStatus, "RUNNING"))));
        return result;
    }

    private AiOpsSummaryVO base(Range range, long total, Map<String, Long> statuses,
                                Map<String, Long> dimensions, List<Long> durations) {
        AiOpsSummaryVO result = new AiOpsSummaryVO();
        result.setFrom(range.from());
        result.setTo(range.to());
        result.setTotalCount(total);
        result.setStatusCounts(statuses);
        result.setDimensionCounts(dimensions);
        result.setP50DurationMs(percentile(durations, 0.50));
        result.setP95DurationMs(percentile(durations, 0.95));
        return result;
    }

    private Map<String, Long> knowledgeQueue() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "PROCESSING", "RETRY_WAIT", "SUCCESS", "DEAD")) {
            result.put(status, eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getStatus, status)));
        }
        return result;
    }

    private <T> Map<String, Long> counts(List<T> rows, Function<T, String> classifier) {
        return rows.stream().collect(Collectors.groupingBy(
                row -> normalize(classifier.apply(row)), LinkedHashMap::new, Collectors.counting()));
    }

    private Long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private LambdaQueryWrapper<AiRagQueryLog> ragRange(Range range) {
        return new LambdaQueryWrapper<AiRagQueryLog>()
                .ge(AiRagQueryLog::getCreateTime, range.from()).le(AiRagQueryLog::getCreateTime, range.to());
    }

    private LambdaQueryWrapper<AiAgentRun> agentRange(Range range) {
        return new LambdaQueryWrapper<AiAgentRun>()
                .ge(AiAgentRun::getCreateTime, range.from()).le(AiAgentRun::getCreateTime, range.to());
    }

    private Range range(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusHours(24) : from;
        if (start.isAfter(end) || start.isBefore(end.minusDays(90))) {
            throw new BusinessException(ErrorCode.OPS_TIME_RANGE_INVALID);
        }
        return new Range(start, end);
    }

    private void requireAdmin() {
        Long actor = UserHolder.get();
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireSystemAdmin(actor);
    }

    private String status(Integer value) {
        AiCallLogStatusEnum status = AiCallLogStatusEnum.fromValue(value);
        return status == null ? "UNKNOWN" : status.name();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private OpsFailureVO failure(String source, String status, String type,
                                 String traceId, LocalDateTime occurredAt) {
        OpsFailureVO result = new OpsFailureVO();
        result.setSource(source);
        result.setStatus(normalize(status));
        result.setFailureType(normalize(type));
        result.setTraceId(traceId);
        result.setOccurredAt(occurredAt);
        return result;
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }
}
