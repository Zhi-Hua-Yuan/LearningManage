package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

class TaskCreationWritePathContractTest {

    @Test
    void productionTaskInsertIsCentralized() throws Exception {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("TaskCreationServiceImpl.java"))
                    .filter(path -> read(path).contains("taskMapper.insert("))
                    .toList();
            Assertions.assertTrue(offenders.isEmpty(), "direct task insert outside shared writer: " + offenders);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aiBreakdownUsesSharedCreationService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/service/impl/ai/scene/TaskBreakdownAiServiceImpl.java"));
        Assertions.assertTrue(source.contains("taskCreationService.createTask"));
    }
}
