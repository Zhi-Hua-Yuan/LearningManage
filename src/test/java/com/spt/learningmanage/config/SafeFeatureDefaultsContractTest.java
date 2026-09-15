package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFeatureDefaultsContractTest {

    @Test
    void agentWorkerAndToolCallingRequireExplicitEnablement() throws IOException {
        String shared = read("src/main/resources/application.yml");
        String dev = read("src/main/resources/application-dev.yml");
        String test = read("src/main/resources/application-test.yml");
        String prod = read("src/main/resources/application-prod.yml");

        assertTrue(shared.contains("enabled: ${AI_AGENT_ENABLED:false}"));
        assertTrue(shared.contains("worker-enabled: ${AI_AGENT_WORKER_ENABLED:false}"));
        assertTrue(shared.contains("tool-calling-enabled: ${AI_AGENT_TOOL_CALLING_ENABLED:false}"));
        assertTrue(dev.contains("enabled: ${AI_AGENT_ENABLED:false}"));
        assertTrue(dev.contains("worker-enabled: ${AI_AGENT_WORKER_ENABLED:false}"));
        assertTrue(dev.contains("tool-calling-enabled: ${AI_AGENT_TOOL_CALLING_ENABLED:false}"));
        assertTrue(test.contains("enabled: false"));
        assertTrue(test.contains("worker-enabled: false"));
        assertTrue(test.contains("tool-calling-enabled: false"));

        for (String profile : new String[]{shared, dev, test, prod}) {
            assertFalse(profile.contains("AI_AGENT_ENABLED:true"));
            assertFalse(profile.contains("AI_AGENT_WORKER_ENABLED:true"));
            assertFalse(profile.contains("AI_AGENT_TOOL_CALLING_ENABLED:true"));
        }
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
