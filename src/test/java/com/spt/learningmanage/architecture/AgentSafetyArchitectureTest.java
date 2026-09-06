package com.spt.learningmanage.architecture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSafetyArchitectureTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void asynchronousAgentKernelNeverReadsThreadLocalUserHolder() throws Exception {
        for (Path path : agentKernelFiles()) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertFalse(source.contains("UserHolder"), "async Agent kernel must use explicit actor context: " + path);
        }
    }

    @Test
    void registeredToolsUseOnlyReadRepositoryPermissionAndRagDependencies() throws Exception {
        Path toolRoot = ROOT.resolve("src/main/java/com/spt/learningmanage/agent/tool");
        try (var files = Files.list(toolRoot)) {
            for (Path path : files.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                assertTrue(source.contains("PermissionService"), "Tool must re-authorize: " + path);
                assertFalse(source.contains("TaskService"), "Tool must not call business mutation service: " + path);
                assertFalse(source.contains("ProjectService"), "Tool must not call business mutation service: " + path);
                assertFalse(source.contains("delete("), "Tool must not contain delete operations: " + path);
                assertFalse(source.contains("update("), "Tool must not contain update operations: " + path);
                assertFalse(source.contains("insert("), "Tool must not contain insert operations: " + path);
            }
        }
    }

    private java.util.List<Path> agentKernelFiles() throws Exception {
        Path root = ROOT.resolve("src/main/java/com/spt/learningmanage/agent");
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(value -> value.toString().endsWith(".java")).toList();
        }
    }
}
