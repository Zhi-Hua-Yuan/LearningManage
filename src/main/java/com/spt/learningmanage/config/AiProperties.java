package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}