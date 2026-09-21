package com.spt.learningmanage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class RagStreamingExecutorConfiguration {

    @Bean(name = "ragStreamingExecutor", destroyMethod = "shutdown")
    public ExecutorService ragStreamingExecutor(RagProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-stream-");
        executor.setCorePoolSize(properties.getStreamWorkerConcurrency());
        executor.setMaxPoolSize(properties.getStreamWorkerConcurrency());
        executor.setQueueCapacity(properties.getStreamQueueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}
