package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    
    /**
     * 大模型 API Key
     */
    private String apiKey;
    
    /**
     * 兼容 OpenAI 的接口基础地址
     */
    private String baseUrl;
    
    /**
     * 使用的模型名称
     */
    private String model;

    private String breakdownModel;

    private String polishModel;

    private String fallbackModel;

    /**
     * AI HTTP 建立连接的超时时间。
     */
    private Integer connectTimeoutMs = 5000;

    /**
     * AI HTTP 等待响应数据的超时时间。
     */
    private Integer readTimeoutMs = 60000;

    private Chat chat = new Chat();

    private Logging logging = new Logging();

    private Resilience resilience = new Resilience();

    private Pricing pricing = new Pricing();

    @Data
    public static class Chat {
        private boolean enabled = true;
    }

    @Data
    public static class Logging {
        private int maxBodyChars = 8000;
        private int maxErrorChars = 2000;
    }

    @Data
    public static class Resilience {
        private int maxConcurrentCalls = 20;
        private int maxWaitMillis = 0;
        private int totalTimeoutMs = 120000;
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 10;
        private float failureRateThreshold = 50.0f;
        private int slowCallDurationMs = 30000;
        private float slowCallRateThreshold = 50.0f;
        private int openStateWaitMs = 30000;
        private int halfOpenPermittedCalls = 3;
    }

    @Data
    public static class Pricing {
        private String version;
        private String currency = "CNY";
        private Map<String, ModelPrice> models = new HashMap<>();
    }

    @Data
    public static class ModelPrice {
        private BigDecimal inputPerMillion;
        private BigDecimal outputPerMillion;
    }
}
