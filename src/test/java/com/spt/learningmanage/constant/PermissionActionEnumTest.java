package com.spt.learningmanage.constant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionActionEnumTest {

    @Test
    void shouldCoverFrozenPermissionActionSetWithoutDuplicateValues() {
        Set<String> values = Arrays.stream(PermissionActionEnum.values())
                .map(PermissionActionEnum::getValue)
                .collect(Collectors.toSet());

        assertEquals(PermissionActionEnum.values().length, values.size());
        assertEquals(22, values.size());
        assertTrue(values.contains("PROJECT_VIEW"));
        assertTrue(values.contains("TASK_ASSIGN"));
        assertTrue(values.contains("PRIVATE_REVIEW_DISCOVER"));
    }

    @Test
    void taskCreateShouldCanonicalizeToProjectCreateTask() {
        assertEquals(
                PermissionActionEnum.PROJECT_CREATE_TASK,
                PermissionActionEnum.TASK_CREATE.canonical()
        );
        assertEquals(
                PermissionActionEnum.TASK_VIEW,
                PermissionActionEnum.TASK_VIEW.canonical()
        );
    }

    @Test
    void enumShouldNotDefineGlobalSystemAdminBypassAction() {
        assertFalse(Arrays.stream(PermissionActionEnum.values())
                .anyMatch(action -> action.getValue().contains("BYPASS")));
    }
}
