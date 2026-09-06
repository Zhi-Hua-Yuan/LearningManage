package com.spt.learningmanage.agent;

import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunStateMachineTest {
    private final AgentRunStateMachine stateMachine = new AgentRunStateMachine();

    @Test
    void permitsOnlyPendingAndRunningTransitions() {
        assertTrue(stateMachine.canTransition(AgentRunStatusEnum.PENDING, AgentRunStatusEnum.RUNNING));
        assertTrue(stateMachine.canTransition(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.PARTIAL));
        assertFalse(stateMachine.canTransition(AgentRunStatusEnum.SUCCEEDED, AgentRunStatusEnum.CANCELED));
        assertThrows(BusinessException.class,
                () -> stateMachine.requireTransition(AgentRunStatusEnum.FAILED, AgentRunStatusEnum.RUNNING));
    }
}

