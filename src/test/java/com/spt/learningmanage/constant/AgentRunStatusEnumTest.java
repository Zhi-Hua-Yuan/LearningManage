package com.spt.learningmanage.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunStatusEnumTest {
    @Test
    void onlyFinalStatesAreTerminal() {
        assertFalse(AgentRunStatusEnum.PENDING.isTerminal());
        assertFalse(AgentRunStatusEnum.RUNNING.isTerminal());
        assertTrue(AgentRunStatusEnum.SUCCEEDED.isTerminal());
        assertTrue(AgentRunStatusEnum.PARTIAL.isTerminal());
        assertTrue(AgentRunStatusEnum.FAILED.isTerminal());
        assertTrue(AgentRunStatusEnum.TIMED_OUT.isTerminal());
        assertTrue(AgentRunStatusEnum.CANCELED.isTerminal());
    }
}
