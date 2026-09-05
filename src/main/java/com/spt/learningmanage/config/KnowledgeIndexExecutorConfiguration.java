package com.spt.learningmanage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class KnowledgeIndexExecutorConfiguration {

    @Bean(name = "knowledgeIndexExecutor")
    public Executor knowledgeIndexExecutor(KnowledgeIndexProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("knowledge-index-");
        executor.setCorePoolSize(properties.getWorkerConcurrency());
        executor.setMaxPoolSize(properties.getWorkerConcurrency());
        executor.setQueueCapacity(properties.getWorkerConcurrency() * 4);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
