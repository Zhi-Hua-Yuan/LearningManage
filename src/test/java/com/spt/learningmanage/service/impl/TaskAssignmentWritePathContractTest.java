package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class TaskAssignmentWritePathContractTest {

    @Test
    void productionTaskAssignmentCasIsCentralized() throws Exception {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("TaskAssignmentServiceImpl.java"))
                    .filter(path -> !path.getFileName().toString().equals("TaskMapper.java"))
                    .filter(path -> read(path).contains("compareAndSetAssignee("))
                    .toList();
            Assertions.assertTrue(offenders.isEmpty(),
                    "task assignment CAS outside shared writer: " + offenders);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
