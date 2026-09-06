package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.agent.AgentRunStateMachine;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunServiceImplTest {
    private final AiAgentRunMapper mapper = mock(AiAgentRunMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AgentProperties properties = new AgentProperties();
    private final AgentRunServiceImpl service = new AgentRunServiceImpl(
            mapper, permissionService, properties, new AgentRunStateMachine());

    @AfterEach
    void cleanup() {
        UserHolder.remove();
    }

    @Test
    void disabledAgentFailsBeforeCreatingRun() {
        AgentProjectRiskRequest request = request();
        assertThrows(BusinessException.class, () -> service.submitProjectRisk(request));
    }

    @Test
    void submissionAuthorizesAndCreatesDurablePendingRun() {
        properties.setEnabled(true);
        properties.setToolCallingEnabled(true);
        UserHolder.set(7L);
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(AiAgentRun.class))).thenReturn(1);

        var result = service.submitProjectRisk(request());

        assertEquals(AgentRunStatusEnum.PENDING.name(), result.status());
        verify(permissionService).requireProjectView(7L, 99L);
        verify(mapper).insert(any(AiAgentRun.class));
    }

    @Test
    void sameClientRequestReturnsExistingRun() {
        properties.setEnabled(true);
        UserHolder.set(7L);
        AiAgentRun existing = new AiAgentRun();
        existing.setRunId("existing");
        existing.setStatus("RUNNING");
        when(mapper.selectOne(any())).thenReturn(existing);

        var result = service.submitProjectRisk(request());

        assertEquals("existing", result.runId());
        assertEquals("RUNNING", result.status());
    }

    private AgentProjectRiskRequest request() {
        AgentProjectRiskRequest request = new AgentProjectRiskRequest();
        request.setProjectId(99L);
        request.setClientRequestId("request-1");
        return request;
    }
}
