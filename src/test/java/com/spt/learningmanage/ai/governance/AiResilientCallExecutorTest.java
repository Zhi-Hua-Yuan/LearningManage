package com.spt.learningmanage.ai.governance;

import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiResilientCallExecutorTest {

    @Test
    void shouldOpenCircuitAfterConfiguredFailures() {
        AiProperties properties = new AiProperties();
        properties.getResilience().setSlidingWindowSize(2);
        properties.getResilience().setMinimumNumberOfCalls(2);
        properties.getResilience().setFailureRateThreshold(50);
        AiResilientCallExecutor executor = new AiResilientCallExecutor(properties);
        AtomicInteger calls = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            assertThrows(AiInvocationException.class, () -> executor.execute(
                    "primary", "primary", 0, () -> {
                        calls.incrementAndGet();
                        throw failure(AiFailureTypeEnum.TIMEOUT);
                    }));
        }

        AiInvocationException rejected = assertThrows(AiInvocationException.class,
                () -> executor.execute("primary", "primary", 0, () -> {
                    calls.incrementAndGet();
                    return "unexpected";
                }));
        assertEquals(AiFailureTypeEnum.CIRCUIT_OPEN, rejected.getFailureType());
        assertEquals(2, calls.get());
    }

    @Test
    void shouldRejectExcessConcurrencyWithoutExecutingSupplier() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getResilience().setMaxConcurrentCalls(1);
        properties.getResilience().setMaxWaitMillis(0);
        AiResilientCallExecutor executor = new AiResilientCallExecutor(properties);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = pool.submit(() -> executor.execute("primary", "primary", 0, () -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return "ok";
            }));
            entered.await(5, TimeUnit.SECONDS);

            AiInvocationException rejected = assertThrows(AiInvocationException.class,
                    () -> executor.execute("primary", "primary", 0, () -> "unexpected"));
            assertEquals(AiFailureTypeEnum.CONCURRENCY_LIMIT, rejected.getFailureType());

            release.countDown();
            assertEquals("ok", first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void shouldNotOpenCircuitForConfigurationFailures() {
        AiProperties properties = new AiProperties();
        properties.getResilience().setSlidingWindowSize(2);
        properties.getResilience().setMinimumNumberOfCalls(2);
        AiResilientCallExecutor executor = new AiResilientCallExecutor(properties);

        for (int i = 0; i < 3; i++) {
            assertThrows(AiInvocationException.class, () -> executor.execute(
                    "primary", "primary", 0,
                    () -> { throw failure(AiFailureTypeEnum.CONFIG_ERROR); }));
        }

        assertEquals("ok", executor.execute("primary", "primary", 0, () -> "ok"));
    }

    @Test
    void shouldRecoverThroughHalfOpenProbe() {
        AiProperties properties = new AiProperties();
        properties.getResilience().setHalfOpenPermittedCalls(1);
        AiResilientCallExecutor executor = new AiResilientCallExecutor(properties);
        executor.circuitBreaker("primary").transitionToOpenState();
        executor.circuitBreaker("primary").transitionToHalfOpenState();

        assertEquals("recovered", executor.execute("primary", "primary", 0, () -> "recovered"));
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                executor.circuitBreaker("primary").getState());
    }

    private AiInvocationException failure(AiFailureTypeEnum type) {
        return new AiInvocationException(type, "primary", 0, "safe", "internal", null);
    }
}
