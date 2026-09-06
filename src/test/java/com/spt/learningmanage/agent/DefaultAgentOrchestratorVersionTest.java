package com.spt.learningmanage.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAgentOrchestratorVersionTest {
    @Test
    void changedDataKeepsStartingVersionSoDraftConfirmationFailsClosed() {
        assertEquals(7L, DefaultAgentOrchestrator.stableReportVersion(7L, 8L));
        assertEquals(7L, DefaultAgentOrchestrator.stableReportVersion(7L, 7L));
    }
}
