package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecretPolicy {

    private final JwtProperties jwtProperties;
    private final String databasePassword;
    private final String aiApiKey;

    private final boolean aiChatEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductionSecretPolicy(
            JwtProperties jwtProperties,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${ai.api-key:}") String aiApiKey,
            @Value("${ai.chat.enabled:true}") boolean aiChatEnabled) {
        this.jwtProperties = jwtProperties;
        this.databasePassword = databasePassword;
        this.aiApiKey = aiApiKey;
        this.aiChatEnabled = aiChatEnabled;
    }

    public ProductionSecretPolicy(JwtProperties jwtProperties,
                                  String databasePassword,
                                  String aiApiKey) {
        this(jwtProperties, databasePassword, aiApiKey, true);
    }

    @PostConstruct
    public void validate() {
        requireUsable("DB_PASSWORD", databasePassword);
        if (aiChatEnabled) {
            requireUsable("ALIYUN_API_KEY", aiApiKey);
        }
        requireUsable("JWT_SECRET", jwtProperties.getSecret());
        if (jwtProperties.getSecret().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }
    }

    private void requireUsable(String variableName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured for production");
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.contains("${")
                || normalized.contains("please_set")
                || normalized.contains("replace_with")
                || normalized.contains("changeme")
                || normalized.contains("default_secret")
                || "root".equals(normalized)
                || "password".equals(normalized)) {
            throw new IllegalStateException(variableName + " contains a forbidden placeholder or default value");
        }
    }
}
