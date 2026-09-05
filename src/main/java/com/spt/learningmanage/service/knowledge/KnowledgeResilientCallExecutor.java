package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class KnowledgeResilientCallExecutor {

    private final Map<KnowledgeDependencyType, Bulkhead> bulkheads =
            new EnumMap<>(KnowledgeDependencyType.class);
    private final Map<KnowledgeDependencyType, CircuitBreaker> circuits =
            new EnumMap<>(KnowledgeDependencyType.class);

    public KnowledgeResilientCallExecutor(KnowledgeIndexProperties properties) {
        bulkheads.put(KnowledgeDependencyType.EMBEDDING,
                bulkhead("knowledge-embedding", properties.getEmbeddingMaxConcurrentCalls()));
        bulkheads.put(KnowledgeDependencyType.VECTOR_STORE,
                bulkhead("knowledge-vector-store", properties.getVectorMaxConcurrentCalls()));
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getCircuitSlidingWindowSize())
                .minimumNumberOfCalls(properties.getCircuitMinimumCalls())
                .failureRateThreshold(properties.getCircuitFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(properties.getCircuitOpenWaitMs()))
                .recordException(this::recordFailure)
                .build();
        circuits.put(KnowledgeDependencyType.EMBEDDING,
                CircuitBreaker.of("knowledge-embedding", config));
        circuits.put(KnowledgeDependencyType.VECTOR_STORE,
                CircuitBreaker.of("knowledge-vector-store", config));
    }

    public <T> T execute(KnowledgeDependencyType dependency, Supplier<T> action) {
        Supplier<T> guarded = Bulkhead.decorateSupplier(bulkheads.get(dependency),
                CircuitBreaker.decorateSupplier(circuits.get(dependency), action));
        try {
            return guarded.get();
        } catch (BulkheadFullException exception) {
            throw new KnowledgeIndexException(type(dependency), true,
                    "知识索引依赖当前请求过多", dependency + " bulkhead rejected call", exception);
        } catch (CallNotPermittedException exception) {
            throw new KnowledgeIndexException(type(dependency), true,
                    "知识索引依赖暂时熔断", dependency + " circuit breaker is open", exception);
        }
    }

    CircuitBreaker circuit(KnowledgeDependencyType dependency) {
        return circuits.get(dependency);
    }

    Bulkhead bulkhead(KnowledgeDependencyType dependency) {
        return bulkheads.get(dependency);
    }

    private Bulkhead bulkhead(String name, int concurrency) {
        return Bulkhead.of(name, BulkheadConfig.custom()
                .maxConcurrentCalls(concurrency)
                .maxWaitDuration(Duration.ZERO)
                .build());
    }

    private boolean recordFailure(Throwable throwable) {
        if (!(throwable instanceof KnowledgeIndexException exception)) {
            return false;
        }
        return exception.isRetryable();
    }

    private KnowledgeFailureTypeEnum type(KnowledgeDependencyType dependency) {
        return dependency == KnowledgeDependencyType.EMBEDDING
                ? KnowledgeFailureTypeEnum.NETWORK : KnowledgeFailureTypeEnum.VECTOR_STORE;
    }
}
