package com.spt.learningmanage.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskAssignmentActionEnumTest {
    @Test
    void resolvesAssignmentTransitions() {
        assertEquals(TaskAssignmentActionEnum.ASSIGN, TaskAssignmentActionEnum.resolve(null, 2L));
        assertEquals(TaskAssignmentActionEnum.REASSIGN, TaskAssignmentActionEnum.resolve(1L, 2L));
        assertEquals(TaskAssignmentActionEnum.UNASSIGN, TaskAssignmentActionEnum.resolve(1L, null));
        assertNull(TaskAssignmentActionEnum.resolve(1L, 1L));
    }
}
