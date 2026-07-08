package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit.ai")
public class RateLimitProperties {

    private Boolean enabled = true;

    private Integer windowSeconds = 60;

    private Integer maxRequests = 5;

    private Boolean failOpen = true;
}
