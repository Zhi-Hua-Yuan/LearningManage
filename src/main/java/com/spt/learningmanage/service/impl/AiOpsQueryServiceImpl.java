package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.vo.ops.AiOpsOverviewVO;
import com.spt.learningmanage.model.vo.ops.AiOpsSummaryVO;
import com.spt.learningmanage.model.vo.ops.DependencyStatusVO;
import com.spt.learningmanage.model.vo.ops.OpsFailureVO;
import com.spt.learningmanage.service.AiDependencyHealthService;
import com.spt.learningmanage.service.AiOpsQueryService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class AiOpsQueryServiceImpl implements AiOpsQueryService {
    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String FAILURE_UNION = """
            SELECT 'AI' source,
                   CASE status WHEN 2 THEN 'FAILED' WHEN 3 THEN 'PARSE_FAILED'
                               WHEN 4 THEN 'TIMEOUT' ELSE 'UNKNOWN' END status,
                   COALESCE(failure_type, 'UNKNOWN') failure_type,
                   trace_id, update_time occurred_at
            FROM ai_call_log
            WHERE create_time >= ? AND create_time <= ? AND status IN (2, 3, 4)
            UNION ALL
            SELECT 'RAG', status, COALESCE(failure_type, 'UNKNOWN'), trace_id, update_time
            FROM ai_rag_query_log
            WHERE create_time >= ? AND create_time <= ? AND status = 'FAILED'
            UNION ALL
            SELECT 'AGENT', status, COALESCE(failure_type, 'UNKNOWN'), trace_id, update_time
            FROM ai_agent_run
            WHERE create_time >= ? AND create_time <= ?
              AND status IN ('FAILED', 'TIMED_OUT', 'PARTIAL')
            UNION ALL
            SELECT 'KNOWLEDGE', status, COALESCE(failure_type, 'UNKNOWN'), trace_id, update_time
            FROM ai_knowledge_index_event
            WHERE create_time >= ? AND create_time <= ? AND status = 'DEAD'
            UNION ALL
            SELECT 'CLEANUP', status, status, trace_id, update_time
            FROM ai_data_cleanup_run
            WHERE create_time >= ? AND create_time <= ? AND status IN ('FAILED', 'PARTIAL')
            """;

    private final JdbcTemplate jdbc;
    private final AiDependencyHealthService dependencyHealth;
    private final PermissionService permissionService;

    public AiOpsQueryServiceImpl(JdbcTemplate jdbc,
                                 AiDependencyHealthService dependencyHealth,
                                 PermissionService permissionService) {
        this.jdbc = jdbc;
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
        long offset = Math.min(page - 1, Long.MAX_VALUE / pageSize) * pageSize;
        Object[] rangeArguments = failureRangeArguments(range);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + FAILURE_UNION + ") failures",
                Long.class, rangeArguments);
        List<Object> pageArguments = new ArrayList<>();
        for (Object argument : rangeArguments) {
            pageArguments.add(argument);
        }
        pageArguments.add(pageSize);
        pageArguments.add(offset);
        List<OpsFailureVO> records = jdbc.query(
                "SELECT source,status,failure_type,trace_id,occurred_at FROM ("
                        + FAILURE_UNION
                        + ") failures ORDER BY occurred_at DESC, source, trace_id LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> failure(
                        resultSet.getString("source"),
                        resultSet.getString("status"),
                        resultSet.getString("failure_type"),
                        resultSet.getString("trace_id"),
                        toLocalDateTime(resultSet.getTimestamp("occurred_at"))),
                pageArguments.toArray());
        Page<OpsFailureVO> result = new Page<>(page, pageSize, total == null ? 0 : total);
        result.setRecords(records);
        return result;
    }

    @Override
    public Map<String, DependencyStatusVO> dependencies() {
        requireAdmin();
        dependencyHealth.refresh();
        return dependencyHealth.snapshot();
    }

    private AiOpsSummaryVO ai(Range range) {
        AiOpsSummaryVO result = databaseSummary(
                range, "ai_call_log", "status", "scene", "cost_time_ms",
                value -> status(value instanceof Number number ? number.intValue() : null),
                this::normalize);
        Map<String, Object> usage = jdbc.queryForMap("""
                SELECT COALESCE(SUM(total_tokens), 0) total_tokens,
                       SUM(estimated_cost) estimated_cost,
                       CASE WHEN COUNT(DISTINCT currency)=1 THEN MAX(currency) ELSE NULL END currency
                FROM ai_call_log WHERE create_time >= ? AND create_time <= ?
                """, range.from(), range.to());
        result.setTotalTokens(number(usage.get("total_tokens")).longValue());
        Object estimatedCost = usage.get("estimated_cost");
        result.setEstimatedCost(estimatedCost == null ? null : new BigDecimal(estimatedCost.toString()));
        result.setCurrency(usage.get("currency") == null ? null : usage.get("currency").toString());
        return result;
    }

    private AiOpsSummaryVO rag(Range range) {
        return databaseSummary(
                range, "ai_rag_query_log", "status",
                "CASE WHEN degraded=1 THEN 'degraded' ELSE 'normal' END",
                "duration_ms", this::normalize, this::normalize);
    }

    private AiOpsSummaryVO agent(Range range) {
        AiOpsSummaryVO result = databaseSummary(
                range, "ai_agent_run", "status", "orchestration_mode",
                "GREATEST(TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) DIV 1000, 0)",
                this::normalize, this::normalize);
        result.setQueueCounts(groupCounts(
                "ai_agent_run", "status", null, null,
                value -> normalize(value), List.of("PENDING", "RUNNING")));
        return result;
    }

    private AiOpsSummaryVO databaseSummary(Range range,
                                           String table,
                                           String statusExpression,
                                           String dimensionExpression,
                                           String durationExpression,
                                           Function<Object, String> statusNormalizer,
                                           Function<Object, String> dimensionNormalizer) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE create_time >= ? AND create_time <= ?",
                Long.class, range.from(), range.to());
        AiOpsSummaryVO result = new AiOpsSummaryVO();
        result.setFrom(range.from());
        result.setTo(range.to());
        result.setTotalCount(total == null ? 0 : total);
        result.setStatusCounts(groupCounts(table, statusExpression, range.from(), range.to(),
                statusNormalizer, List.of()));
        result.setDimensionCounts(groupCounts(table, dimensionExpression, range.from(), range.to(),
                dimensionNormalizer, List.of()));
        result.setP50DurationMs(percentile(table, durationExpression, range, 0.50));
        result.setP95DurationMs(percentile(table, durationExpression, range, 0.95));
        return result;
    }

    private Map<String, Long> groupCounts(String table,
                                          String expression,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Function<Object, String> normalizer,
                                          List<String> allowedValues) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(expression).append(" label, COUNT(*) total FROM ").append(table);
        List<Object> arguments = new ArrayList<>();
        if (from != null && to != null) {
            sql.append(" WHERE create_time >= ? AND create_time <= ?");
            arguments.add(from);
            arguments.add(to);
        }
        if (!allowedValues.isEmpty()) {
            sql.append(from == null ? " WHERE " : " AND ")
                    .append(expression).append(" IN (")
                    .append(String.join(",", allowedValues.stream().map(value -> "?").toList()))
                    .append(")");
            arguments.addAll(allowedValues);
        }
        sql.append(" GROUP BY ").append(expression);
        Map<String, Long> result = new LinkedHashMap<>();
        allowedValues.forEach(value -> result.put(value, 0L));
        jdbc.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> result.put(
                normalizer.apply(resultSet.getObject("label")),
                resultSet.getLong("total")), arguments.toArray());
        return result;
    }

    private Long percentile(String table, String durationExpression, Range range, double percentile) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE create_time >= ? AND create_time <= ? AND ("
                        + durationExpression + ") IS NOT NULL",
                Long.class, range.from(), range.to());
        if (count == null || count == 0) {
            return null;
        }
        long offset = Math.max(0, (long) Math.ceil(count * percentile) - 1);
        Number value = jdbc.queryForObject(
                "SELECT " + durationExpression + " duration_ms FROM " + table
                        + " WHERE create_time >= ? AND create_time <= ? AND ("
                        + durationExpression + ") IS NOT NULL"
                        + " ORDER BY duration_ms LIMIT 1 OFFSET ?",
                Number.class, range.from(), range.to(), offset);
        return value == null ? null : Math.max(value.longValue(), 0);
    }

    private Map<String, Long> knowledgeQueue() {
        return groupCounts(
                "ai_knowledge_index_event", "status", null, null,
                value -> normalize(value),
                List.of("PENDING", "PROCESSING", "RETRY_WAIT", "SUCCESS", "DEAD"));
    }

    private Object[] failureRangeArguments(Range range) {
        return new Object[]{
                range.from(), range.to(), range.from(), range.to(), range.from(), range.to(),
                range.from(), range.to(), range.from(), range.to()
        };
    }

    private Range range(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now(BUSINESS_TIME_ZONE) : to;
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

    private String normalize(Object value) {
        if (value == null || value.toString().isBlank()) {
            return "UNKNOWN";
        }
        return value.toString();
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0L;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
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
