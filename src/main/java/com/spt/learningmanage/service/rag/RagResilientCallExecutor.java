package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.RagDependencyException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RagResilientCallExecutor {
    private final Bulkhead rerankBulkhead;
    private final CircuitBreaker rerankCircuit;

    public RagResilientCallExecutor(RerankProperties properties) {
        this.rerankBulkhead = Bulkhead.of("rag-rerank", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(properties.getMaxConcurrentCalls(), 1))
                .maxWaitDuration(Duration.ZERO)
                .build());
        this.rerankCircuit = CircuitBreaker.of("rag-rerank", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(error -> error instanceof RagDependencyException dependency
                        && dependency.isRetryable())
                .build());
    }

    public <T> T executeRerank(Supplier<T> action) {
        Supplier<T> guarded = Bulkhead.decorateSupplier(rerankBulkhead,
                CircuitBreaker.decorateSupplier(rerankCircuit, action));
        try {
            return guarded.get();
        } catch (BulkheadFullException exception) {
            throw unavailable("Rerank concurrency limit reached", exception);
        } catch (CallNotPermittedException exception) {
            throw unavailable("Rerank circuit breaker is open", exception);
        }
    }

    private RagDependencyException unavailable(String internal, Throwable cause) {
        return new RagDependencyException(ErrorCode.RERANK_UNAVAILABLE, true,
                "重排服务暂时不可用", internal, cause);
    }
}
