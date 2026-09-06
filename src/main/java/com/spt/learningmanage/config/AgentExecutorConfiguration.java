package com.spt.learningmanage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AgentExecutorConfiguration {
    @Bean(name = "agentRunExecutor")
    public Executor agentRunExecutor(AgentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("agent-run-");
        executor.setCorePoolSize(properties.getMaxConcurrentRuns());
        executor.setMaxPoolSize(properties.getMaxConcurrentRuns());
        executor.setQueueCapacity(properties.getMaxConcurrentRuns() * 2);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getOverallTimeoutSeconds());
        executor.initialize();
        return executor;
    }

    @Bean(name = "agentToolTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService agentToolExecutor(AgentProperties properties) {
        int size = Math.max(2, properties.getMaxConcurrentRuns());
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(size * 4), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "agentOrchestrationTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService agentOrchestrationTaskExecutor(AgentProperties properties) {
        int size = properties.getMaxConcurrentRuns();
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(size), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "agentHeartbeatTaskExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService agentHeartbeatTaskExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
