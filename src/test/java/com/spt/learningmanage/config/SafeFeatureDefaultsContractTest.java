package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFeatureDefaultsContractTest {

    private static final Map<String, Object> EXPLICIT_AI_SHUTDOWN = Map.of(
            "AI_KNOWLEDGE_WORKER_ENABLED", "false",
            "AI_RAG_ENABLED", "false",
            "AI_AGENT_ENABLED", "false",
            "AI_AGENT_WORKER_ENABLED", "false",
            "AI_AGENT_TOOL_CALLING_ENABLED", "false"
    );

    @Test
    void sharedDevelopmentAndProductionProfilesEnableSafeAiFeaturesByDefault() throws IOException {
        assertDefaultEnabled(environment(null));
        assertDefaultEnabled(environment("dev"));
        assertDefaultEnabled(environment("prod"));
    }

    @Test
    void environmentVariablesCanExplicitlyDisableEveryDefaultEnabledFeature() throws IOException {
        for (String profile : new String[]{null, "dev", "prod"}) {
            StandardEnvironment environment = environment(profile);
            environment.getPropertySources().addFirst(
                    new MapPropertySource("explicit-ai-shutdown", EXPLICIT_AI_SHUTDOWN));

            assertFalse(bind(environment, "ai.knowledge-index", KnowledgeIndexProperties.class).isWorkerEnabled());
            assertFalse(bind(environment, "ai.rag", RagProperties.class).isEnabled());
            AgentProperties agent = bind(environment, "ai.agent", AgentProperties.class);
            assertFalse(agent.isEnabled());
            assertFalse(agent.isWorkerEnabled());
            assertFalse(agent.isToolCallingEnabled());
        }
    }

    @Test
    void testProfileProvidesScenarioLocalShutdownWithoutChangingProductionDefaults() throws IOException {
        StandardEnvironment environment = environment("test");

        assertFalse(bind(environment, "ai.knowledge-index", KnowledgeIndexProperties.class).isWorkerEnabled());
        assertFalse(bind(environment, "ai.rag", RagProperties.class).isEnabled());
        AgentProperties agent = bind(environment, "ai.agent", AgentProperties.class);
        assertFalse(agent.isEnabled());
        assertFalse(agent.isWorkerEnabled());
        assertFalse(agent.isToolCallingEnabled());
    }

    @Test
    void cleanupWorkerRemainsDisabledInEveryProfile() throws IOException {
        for (String profile : new String[]{null, "dev", "test", "prod"}) {
            DataCleanupProperties cleanup = bind(environment(profile), "ai.cleanup", DataCleanupProperties.class);
            assertFalse(cleanup.isEnabled());
            assertFalse(cleanup.isScheduleEnabled());
        }
    }

    private void assertDefaultEnabled(StandardEnvironment environment) {
        assertTrue(bind(environment, "ai.knowledge-index", KnowledgeIndexProperties.class).isWorkerEnabled());
        assertTrue(bind(environment, "ai.rag", RagProperties.class).isEnabled());
        AgentProperties agent = bind(environment, "ai.agent", AgentProperties.class);
        assertTrue(agent.isEnabled());
        assertTrue(agent.isWorkerEnabled());
        assertTrue(agent.isToolCallingEnabled());
    }

    private StandardEnvironment environment(String profile) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        addYaml(environment, "application.yml", false);
        if (profile != null) {
            addYaml(environment, "application-" + profile + ".yml", true);
        }
        return environment;
    }

    private void addYaml(StandardEnvironment environment, String name, boolean first) throws IOException {
        var sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
        for (var source : sources) {
            if (first) {
                environment.getPropertySources().addFirst(source);
            } else {
                environment.getPropertySources().addLast(source);
            }
        }
    }

    private <T> T bind(StandardEnvironment environment, String prefix, Class<T> type) {
        return Binder.get(environment).bind(prefix, type)
                .orElseThrow(() -> new IllegalStateException("Missing configuration: " + prefix));
    }
}
