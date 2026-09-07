package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.AiDependencyStatusEnum;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.mapper.AiRagQueryLogMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.entity.AiRagQueryLog;
import com.spt.learningmanage.model.vo.ops.DependencyStatusVO;
import com.spt.learningmanage.service.AiDependencyHealthService;
import com.spt.learningmanage.service.AiCallLogOperationsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiDependencyHealthServiceImpl implements AiDependencyHealthService {
    private final AiProperties ai;
    private final EmbeddingProperties embedding;
    private final RerankProperties rerank;
    private final QdrantProperties qdrant;
    private final RagProperties rag;
    private final AgentProperties agent;
    private final KnowledgeIndexProperties knowledge;
    private final StringRedisTemplate redis;
    private final AiCallLogOperationsService callLogOperations;
    private final AiRagQueryLogMapper ragLogMapper;
    private final AiAgentRunMapper agentRunMapper;
    private final AiKnowledgeIndexEventMapper eventMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicReference<Map<String, DependencyStatusVO>> cache =
            new AtomicReference<>(Collections.emptyMap());

    public AiDependencyHealthServiceImpl(AiProperties ai,
                                         EmbeddingProperties embedding,
                                         RerankProperties rerank,
                                         QdrantProperties qdrant,
                                         RagProperties rag,
                                         AgentProperties agent,
                                         KnowledgeIndexProperties knowledge,
                                         ObjectProvider<StringRedisTemplate> redis,
                                         AiCallLogOperationsService callLogOperations,
                                         AiRagQueryLogMapper ragLogMapper,
                                         AiAgentRunMapper agentRunMapper,
                                         AiKnowledgeIndexEventMapper eventMapper) {
        this.ai = ai;
        this.embedding = embedding;
        this.rerank = rerank;
        this.qdrant = qdrant;
        this.rag = rag;
        this.agent = agent;
        this.knowledge = knowledge;
        this.redis = redis.getIfAvailable();
        this.callLogOperations = callLogOperations;
        this.ragLogMapper = ragLogMapper;
        this.agentRunMapper = agentRunMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public Map<String, DependencyStatusVO> snapshot() {
        Map<String, DependencyStatusVO> value = cache.get();
        if (value.isEmpty()) {
            refresh();
            value = cache.get();
        }
        return value;
    }

    @Override
    public DependencyStatusVO status(String dependency) {
        return snapshot().getOrDefault(dependency,
                value(dependency, AiDependencyStatusEnum.UNKNOWN, "no cached status"));
    }

    @Override
    @Scheduled(fixedDelayString = "${management.ai-health.refresh-ms:30000}")
    public void refresh() {
        Map<String, DependencyStatusVO> values = new LinkedHashMap<>();
        values.put("redis", probeRedis());
        values.put("qdrant", probeQdrant());
        values.put("chat", chatStatus());
        values.put("embedding", embeddingStatus());
        values.put("rerank", rerankStatus());
        values.put("agentWorker", agentWorkerStatus());
        values.put("knowledgeWorker", knowledgeWorkerStatus());
        cache.set(Collections.unmodifiableMap(values));
    }

    private DependencyStatusVO probeRedis() {
        if (redis == null) {
            return value("redis", AiDependencyStatusEnum.UNKNOWN, "client unavailable");
        }
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return value("redis", "PONG".equalsIgnoreCase(pong)
                    ? AiDependencyStatusEnum.UP : AiDependencyStatusEnum.DEGRADED, "ping");
        } catch (RuntimeException exception) {
            return value("redis", AiDependencyStatusEnum.DEGRADED, "ping failed");
        }
    }

    private DependencyStatusVO probeQdrant() {
        if ((!rag.isEnabled() && !knowledge.isWorkerEnabled()) || blank(qdrant.getBaseUrl())) {
            return value("qdrant", AiDependencyStatusEnum.DISABLED, "feature disabled");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(qdrant.getBaseUrl()) + "/healthz"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            int code = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return value("qdrant", code >= 200 && code < 300
                    ? AiDependencyStatusEnum.UP : AiDependencyStatusEnum.DEGRADED, "http=" + code);
        } catch (Exception exception) {
            return value("qdrant", AiDependencyStatusEnum.DEGRADED, "probe failed");
        }
    }

    private DependencyStatusVO chatStatus() {
        if (!ai.getChat().isEnabled()) {
            return value("chat", AiDependencyStatusEnum.DISABLED, "feature disabled");
        }
        if (blank(ai.getApiKey()) || blank(ai.getBaseUrl())) {
            return value("chat", AiDependencyStatusEnum.DOWN, "configuration missing");
        }
        try {
            AiCallLog latest = callLogOperations.latestMetadata();
            if (latest == null || latest.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(15))) {
                return value("chat", AiDependencyStatusEnum.UNKNOWN, "no recent paid call");
            }
            return value("chat", Objects.equals(latest.getStatus(), AiCallLogStatusEnum.SUCCESS.getValue())
                    ? AiDependencyStatusEnum.UP : AiDependencyStatusEnum.DEGRADED, "recent call metadata");
        } catch (RuntimeException exception) {
            return value("chat", AiDependencyStatusEnum.UNKNOWN, "metadata unavailable");
        }
    }

    private DependencyStatusVO embeddingStatus() {
        if (!rag.isEnabled() && !knowledge.isWorkerEnabled()) {
            return value("embedding", AiDependencyStatusEnum.DISABLED, "feature disabled");
        }
        return value("embedding", blank(embedding.getApiKey()) || blank(embedding.getBaseUrl())
                ? AiDependencyStatusEnum.DOWN : AiDependencyStatusEnum.UNKNOWN,
                blank(embedding.getApiKey()) ? "configuration missing" : "no paid probe");
    }

    private DependencyStatusVO rerankStatus() {
        if (!rag.isEnabled()) {
            return value("rerank", AiDependencyStatusEnum.DISABLED, "feature disabled");
        }
        if (blank(rerank.getApiKey()) || blank(rerank.getBaseUrl())) {
            return value("rerank", AiDependencyStatusEnum.DOWN, "configuration missing");
        }
        try {
            AiRagQueryLog latest = ragLogMapper.selectOne(new LambdaQueryWrapper<AiRagQueryLog>()
                    .orderByDesc(AiRagQueryLog::getCreateTime).last("limit 1"));
            if (latest == null || latest.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(15))) {
                return value("rerank", AiDependencyStatusEnum.UNKNOWN, "no recent query");
            }
            return value("rerank", "FAILED".equals(latest.getStatus())
                    ? AiDependencyStatusEnum.DEGRADED : AiDependencyStatusEnum.UP, "recent query metadata");
        } catch (RuntimeException exception) {
            return value("rerank", AiDependencyStatusEnum.UNKNOWN, "metadata unavailable");
        }
    }

    private DependencyStatusVO agentWorkerStatus() {
        if (!agent.isEnabled() || !agent.isWorkerEnabled()) {
            return value("agentWorker", AiDependencyStatusEnum.DISABLED, "worker disabled");
        }
        try {
            long stale = agentRunMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>()
                    .eq(AiAgentRun::getStatus, "RUNNING")
                    .lt(AiAgentRun::getHeartbeatAt,
                            LocalDateTime.now().minusSeconds(agent.getHeartbeatSeconds() * 2L)));
            return value("agentWorker", stale > 0
                    ? AiDependencyStatusEnum.DEGRADED : AiDependencyStatusEnum.UP,
                    "stale_runs=" + stale);
        } catch (RuntimeException exception) {
            return value("agentWorker", AiDependencyStatusEnum.UNKNOWN, "metadata unavailable");
        }
    }

    private DependencyStatusVO knowledgeWorkerStatus() {
        if (!knowledge.isWorkerEnabled()) {
            return value("knowledgeWorker", AiDependencyStatusEnum.DISABLED, "worker disabled");
        }
        try {
            long stale = eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getStatus, "PROCESSING")
                    .lt(AiKnowledgeIndexEvent::getLeaseUntil, LocalDateTime.now()));
            return value("knowledgeWorker", stale > 0
                    ? AiDependencyStatusEnum.DEGRADED : AiDependencyStatusEnum.UP,
                    "expired_leases=" + stale);
        } catch (RuntimeException exception) {
            return value("knowledgeWorker", AiDependencyStatusEnum.UNKNOWN, "metadata unavailable");
        }
    }

    private DependencyStatusVO value(String name, AiDependencyStatusEnum status, String detail) {
        return new DependencyStatusVO(name, status.name(), detail, LocalDateTime.now());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
