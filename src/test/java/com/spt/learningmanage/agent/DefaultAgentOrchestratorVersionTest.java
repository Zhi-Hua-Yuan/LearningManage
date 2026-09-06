package com.spt.learningmanage.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAgentOrchestratorVersionTest {
    @Test
    void changedDataKeepsStartingVersionSoDraftConfirmationFailsClosed() {
        assertEquals(7L, DefaultAgentOrchestrator.stableReportVersion(7L, 8L));
        assertEquals(7L, DefaultAgentOrchestrator.stableReportVersion(7L, 7L));
        assertEquals(List.of(), DefaultAgentOrchestrator.permittedEvidenceIds(
                List.of("O1", "T1"), Set.of()));
        assertEquals(List.of("S1"), DefaultAgentOrchestrator.permittedEvidenceIds(
                List.of("O1", "S1", "S9"), Set.of("S1", "S2")));
    }
}
