package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentConfigurationValidatorTest {
    @Test
    void defaultsAreSafeAndDisabled() {
        AgentProperties properties = new AgentProperties();
        assertDoesNotThrow(() -> new AgentConfigurationValidator(properties).validate());
    }

    @Test
    void workerAndToolCallingRequireAgentFeature() {
        AgentProperties properties = new AgentProperties();
        properties.setWorkerEnabled(true);
        assertThrows(IllegalStateException.class,
                () -> new AgentConfigurationValidator(properties).validate());
        properties.setWorkerEnabled(false);
        properties.setToolCallingEnabled(true);
        assertThrows(IllegalStateException.class,
                () -> new AgentConfigurationValidator(properties).validate());
    }

    @Test
    void unsafeBoundsFailFast() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolCalls(5);
        assertThrows(IllegalStateException.class,
                () -> new AgentConfigurationValidator(properties).validate());
    }
}
