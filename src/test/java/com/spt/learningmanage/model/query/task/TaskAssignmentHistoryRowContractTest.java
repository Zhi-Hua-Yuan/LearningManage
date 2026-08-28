package com.spt.learningmanage.model.query.task;

import com.spt.learningmanage.model.vo.task.TaskAssignmentHistoryVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskAssignmentHistoryRowContractTest {

    @Test
    void rowIsFlatAndContainsNoEntityOrPrivateAccountFields() {
        Set<String> actual = Arrays.stream(TaskAssignmentHistoryRow.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("id", "taskId", "action", "fromAssigneeUserId", "fromAssigneeUsername",
                "toAssigneeUserId", "toAssigneeUsername", "assignedByUserId", "assignedByUsername",
                "reason", "createTime"), actual);
        assertFalse(Arrays.stream(TaskAssignmentHistoryRow.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getName().equals("com.spt.learningmanage.model.entity.User")
                        || field.getType().getName().equals("com.spt.learningmanage.model.entity.Task")));
        assertFalse(TaskAssignmentHistoryVO.class.isAssignableFrom(TaskAssignmentHistoryRow.class));
    }
}
