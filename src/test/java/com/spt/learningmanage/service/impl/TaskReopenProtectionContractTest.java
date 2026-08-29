package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class TaskReopenProtectionContractTest {

    @Test
    void reopenCasMustCompareStatusAndAssigneeWithNullSafeEquality() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/mapper/TaskMapper.java"));
        Assertions.assertTrue(mapper.contains("compareAndSetStatusForReopen("));
        Assertions.assertTrue(mapper.contains("AND status = #{expectedStatus}"));
        Assertions.assertTrue(mapper.contains("AND assignee_user_id <=> #{expectedAssigneeUserId}"));
        Assertions.assertTrue(mapper.contains("SET status = #{targetStatus}"));
        Assertions.assertTrue(mapper.contains("completed_at = #{completedAt}"));
    }

    @Test
    void reopenCasWritePathMustBeCentralized() throws Exception {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("TaskServiceImpl.java"))
                    .filter(path -> !path.getFileName().toString().equals("TaskMapper.java"))
                    .filter(path -> read(path).contains("compareAndSetStatusForReopen("))
                    .toList();
            Assertions.assertTrue(offenders.isEmpty(),
                    "reopen CAS outside shared status writer: " + offenders);
        }
    }

    @Test
    void reopenProtectionMustBeDeclaredInPolicyContract() throws Exception {
        String policy = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/service/TaskAssigneePolicy.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/spt/learningmanage/service/impl/TaskServiceImpl.java"));
        Assertions.assertTrue(policy.contains("validateReopenAssignee("));
        Assertions.assertTrue(service.contains("validateReopenAssignee("));
        Assertions.assertTrue(service.indexOf("validateReopenAssignee(")
                < service.indexOf("compareAndSetStatusForReopen("));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
