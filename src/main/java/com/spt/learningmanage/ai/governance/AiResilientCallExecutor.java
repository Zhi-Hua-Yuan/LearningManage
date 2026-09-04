package com.spt.learningmanage.ai.governance;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiResilientCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiResilientCallExecutor.class);

    private final Bulkhead globalBulkhead;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Set<String> observedCircuitNames = ConcurrentHashMap.newKeySet();

    public AiResilientCallExecutor(AiProperties properties) {
        AiProperties.Resilience resilience = properties.getResilience();
        this.globalBulkhead = Bulkhead.of("ai-chat-global", BulkheadConfig.custom()
                .maxConcurrentCalls(resilience.getMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(resilience.getMaxWaitMillis()))
                .build());
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(resilience.getSlidingWindowSize())
                .minimumNumberOfCalls(resilience.getMinimumNumberOfCalls())
                .failureRateThreshold(resilience.getFailureRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(resilience.getSlowCallDurationMs()))
                .slowCallRateThreshold(resilience.getSlowCallRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(resilience.getOpenStateWaitMs()))
                .permittedNumberOfCallsInHalfOpenState(resilience.getHalfOpenPermittedCalls())
                .recordException(this::isCircuitFailure)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    public <T> T execute(String requestedModel,
                         String actualModel,
                         int retryCount,
                         Supplier<T> action) {
        CircuitBreaker circuitBreaker = circuitBreaker(actualModel);
        Supplier<T> protectedCall = Bulkhead.decorateSupplier(globalBulkhead,
                CircuitBreaker.decorateSupplier(circuitBreaker, action));
        try {
            return protectedCall.get();
        } catch (BulkheadFullException exception) {
            throw new AiInvocationException(
                    AiFailureTypeEnum.CONCURRENCY_LIMIT,
                    requestedModel,
                    actualModel,
                    retryCount,
                    "AI 服务当前请求较多，请稍后重试",
                    "AI global bulkhead rejected call: model=" + actualModel,
                    exception,
                    null,
                    retryCount > 0,
                    null
            );
        } catch (CallNotPermittedException exception) {
            throw new AiInvocationException(
                    AiFailureTypeEnum.CIRCUIT_OPEN,
                    requestedModel,
                    actualModel,
                    retryCount,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI model circuit breaker is open: model=" + actualModel,
                    exception,
                    null,
                    retryCount > 0,
                    null
            );
        }
    }

    CircuitBreaker circuitBreaker(String model) {
        String name = circuitName(model);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        if (observedCircuitNames.add(name)) {
            circuitBreaker.getEventPublisher().onStateTransition(event ->
                    log.warn("AI circuit state transition: circuit={}, transition={}",
                            name, event.getStateTransition()));
        }
        return circuitBreaker;
    }

    Bulkhead globalBulkhead() {
        return globalBulkhead;
    }

    private boolean isCircuitFailure(Throwable throwable) {
        if (!(throwable instanceof AiInvocationException exception)) {
            return false;
        }
        return switch (exception.getFailureType()) {
            case TIMEOUT, NETWORK_ERROR, RATE_LIMITED, UPSTREAM_SERVER_ERROR, INVALID_RESPONSE -> true;
            default -> false;
        };
    }

    private String circuitName(String model) {
        String normalized = model == null ? "unknown" : model.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return "ai-chat-" + normalized;
    }
}
