package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the operational Flyway switch so a profile cannot silently fall back
 * to the business datasource account or enable migrations by default.
 */
class FlywayConfigurationContractTest {

    @Test
    void sharedFlywayPolicyIsExplicitAndSafeByDefault() throws IOException {
        String yaml = read("application.yml");

        assertTrue(yaml.contains("enabled: ${FLYWAY_ENABLED:false}"));
        assertTrue(yaml.contains("locations: classpath:db/migration"));
        assertTrue(yaml.contains("user: ${FLYWAY_DB_USERNAME:__flyway_migrator_not_configured__}"));
        assertTrue(yaml.contains("password: ${FLYWAY_DB_PASSWORD:__flyway_migrator_not_configured__}"));
        assertTrue(yaml.contains("baseline-on-migrate: false"));
        assertTrue(yaml.contains("validate-on-migrate: true"));
        assertTrue(yaml.contains("clean-disabled: true"));
        assertTrue(yaml.contains("out-of-order: false"));
    }

    @Test
    void everyProfileRequiresAnExplicitFlywaySwitchAndDedicatedCredentials() throws IOException {
        for (String profile : new String[]{"dev", "test", "prod"}) {
            String yaml = read("application-" + profile + ".yml");
            assertTrue(yaml.contains("enabled: ${FLYWAY_ENABLED:false}"), profile);
            assertTrue(yaml.contains("user: ${FLYWAY_DB_USERNAME:__flyway_migrator_not_configured__}"), profile);
            assertTrue(yaml.contains("password: ${FLYWAY_DB_PASSWORD:__flyway_migrator_not_configured__}"), profile);
        }
    }

    private String read(String resource) throws IOException {
        try (var inputStream = new ClassPathResource(resource).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
