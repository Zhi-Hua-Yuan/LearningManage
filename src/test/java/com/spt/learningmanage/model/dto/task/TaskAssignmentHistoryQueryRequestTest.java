package com.spt.learningmanage.model.dto.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskAssignmentHistoryQueryRequestTest {

    @Test
    void defaultsFollowHistoryApiContract() {
        TaskAssignmentHistoryQueryRequest request = new TaskAssignmentHistoryQueryRequest();

        assertEquals(1L, request.getCurrent());
        assertEquals(50L, request.getSize());
    }
}
