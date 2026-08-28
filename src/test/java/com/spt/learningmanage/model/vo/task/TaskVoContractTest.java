package com.spt.learningmanage.model.vo.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.TaskCapabilities;
import org.springframework.beans.BeanUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskVoContractTest {

    @Test
    void identityFieldsAreCopiedAndSerializedWithExplicitNames() throws Exception {
        Task task = new Task();
        task.setCreatedByUserId(1001L);
        task.setAssigneeUserId(1002L);
        task.setAssignedByUserId(1000L);
        LocalDateTime assignedAt = LocalDateTime.of(2026, 8, 28, 16, 30);
        task.setAssignedAt(assignedAt);

        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(task, vo);
        vo.setCapabilities(new TaskCapabilities(true, true, false, true, false));

        assertEquals(1001L, vo.getCreatedByUserId());
        assertEquals(1002L, vo.getAssigneeUserId());
        assertEquals(1000L, vo.getAssignedByUserId());
        assertEquals(assignedAt, vo.getAssignedAt());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("\"createdByUserId\""));
        assertTrue(json.contains("\"assigneeUserId\""));
        assertTrue(json.contains("\"assignedByUserId\""));
        assertTrue(json.contains("\"assignedAt\""));
        assertTrue(json.contains("\"capabilities\""));
        assertFalse(json.contains("\"userId\""));
    }
}
