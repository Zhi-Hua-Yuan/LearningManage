package com.spt.learningmanage.service.impl.agent;

import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRunQueueServiceImplTest {
    @Test
    void exhaustedLeaseEmitsFailureOnlyAfterTheTerminalCasWins() {
        AiAgentRunMapper mapper = mock(AiAgentRunMapper.class);
        AiMetricsRecorder metrics = mock(AiMetricsRecorder.class);
        AgentProperties properties = new AgentProperties();
        AiAgentRun exhausted = new AiAgentRun();
        exhausted.setId(1L);
        exhausted.setScene("project-risk");
        exhausted.setOrchestrationMode("TOOL_CALLING");
        exhausted.setStartedAt(LocalDateTime.now().minusSeconds(5));
        when(mapper.selectExhaustedLeasesForUpdate(any(), eq(properties.getMaxAttempts())))
                .thenReturn(List.of(exhausted));
        when(mapper.failExhaustedLease(eq(1L), any(), eq(properties.getMaxAttempts())))
                .thenReturn(1);
        when(mapper.selectClaimableForUpdate(any(), anyInt(), anyInt())).thenReturn(List.of());

        new AgentRunQueueServiceImpl(mapper, properties, metrics).claimReady("worker", 5);

        verify(metrics).recordAgentRun(eq("project-risk"), eq("FAILED"),
                eq("TOOL_CALLING"), longThat(value -> value >= 0));
    }
}
