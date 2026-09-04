package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecretPolicyTest {

    @Test
    void productionPolicyShouldRejectMissingOrPlaceholderSecrets() {
        JwtProperties properties = jwtProperties("replace_with_at_least_32_random_characters");

        ProductionSecretPolicy policy = new ProductionSecretPolicy(properties, "", "replace_with_your_api_key");

        assertThrows(IllegalStateException.class, policy::validate);
    }

    @Test
    void productionPolicyShouldRejectUnresolvedEnvironmentPlaceholders() {
        JwtProperties properties = jwtProperties("${JWT_SECRET}");

        ProductionSecretPolicy policy = new ProductionSecretPolicy(properties, "${DB_PASSWORD}", "${ALIYUN_API_KEY}");

        assertThrows(IllegalStateException.class, policy::validate);
    }

    @Test
    void productionPolicyShouldAcceptConfiguredSecrets() {
        JwtProperties properties = jwtProperties("a-realistic-local-test-secret-with-32-bytes");

        ProductionSecretPolicy policy = new ProductionSecretPolicy(properties, "database-password-for-test", "aliyun-key-for-test");

        assertDoesNotThrow(policy::validate);
    }

    @Test
    void productionPolicyShouldAllowMissingAiKeyWhenChatIsDisabled() {
        JwtProperties properties = jwtProperties("a-realistic-local-test-secret-with-32-bytes");
        ProductionSecretPolicy policy = new ProductionSecretPolicy(
                properties, "database-password-for-test", "", false);

        assertDoesNotThrow(policy::validate);
    }

    private JwtProperties jwtProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpireSeconds(86400);
        return properties;
    }
}
