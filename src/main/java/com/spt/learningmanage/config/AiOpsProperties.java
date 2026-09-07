package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.ops")
public class AiOpsProperties {
    private BigDecimal dailyCostSoftLimit;
    private BigDecimal dailyCostHardLimit;
}
