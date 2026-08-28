package com.spt.learningmanage.model.dto.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskAssignRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expectedAssigneePresenceDistinguishesMissingAndExplicitNull() throws Exception {
        TaskAssignRequest missing = objectMapper.readValue("{\"taskId\":1}", TaskAssignRequest.class);
        assertFalse(missing.isExpectedAssigneeUserIdPresent());

        TaskAssignRequest explicitNull = objectMapper.readValue(
                "{\"taskId\":1,\"expectedAssigneeUserId\":null}", TaskAssignRequest.class);
        assertTrue(explicitNull.isExpectedAssigneeUserIdPresent());
        assertNull(explicitNull.getExpectedAssigneeUserId());
    }
}
