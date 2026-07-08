package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit.ai")
public class RateLimitProperties {

    private Boolean enabled = true;

    private Boolean failOpen = true;

    private Rule defaultRule = new Rule();

    private Map<String, Rule> sceneRules = new HashMap<>();

    @Data
    public static class Rule {

        private Boolean enabled = true;

        private Integer windowSeconds = 60;

        private Integer maxRequests = 5;
    }
}
