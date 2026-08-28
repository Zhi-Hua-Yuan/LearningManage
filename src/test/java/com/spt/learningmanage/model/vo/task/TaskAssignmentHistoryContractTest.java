package com.spt.learningmanage.model.vo.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAssignmentHistoryContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void userSummaryExposesOnlyPublicDisplayFields() {
        assertFieldNames(AssignmentUserSummaryVO.class, Set.of("userId", "username"));
    }

    @Test
    void historyViewExposesOnlyContractFields() {
        assertFieldNames(TaskAssignmentHistoryVO.class,
                Set.of("id", "taskId", "action", "fromAssignee", "toAssignee", "assignedBy", "reason", "createTime"));
    }

    @Test
    void nullAssigneeAndDeletedUserSemanticsSerializeUnambiguously() throws Exception {
        AssignmentUserSummaryVO deletedUser = new AssignmentUserSummaryVO();
        deletedUser.setUserId(1002L);
        deletedUser.setUsername(null);

        TaskAssignmentHistoryVO vo = new TaskAssignmentHistoryVO();
        vo.setId(7L);
        vo.setTaskId(101L);
        vo.setAction("UNASSIGN");
        vo.setFromAssignee(deletedUser);
        vo.setToAssignee(null);
        vo.setAssignedBy(deletedUser);
        vo.setReason("调整安排");
        vo.setCreateTime(LocalDateTime.of(2026, 8, 29, 10, 30));

        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("\"fromAssignee\":{"));
        assertTrue(json.contains("\"userId\":1002"));
        assertTrue(json.contains("\"username\":null"));
        assertTrue(json.contains("\"toAssignee\":null"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("isDelete"));
        assertFalse(json.contains("userRole"));
    }

    private static void assertFieldNames(Class<?> type, Set<String> expected) {
        Set<String> actual = Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }
}
