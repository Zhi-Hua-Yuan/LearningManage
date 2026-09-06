package com.spt.learningmanage.agent;

import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolPolicyTest {
    private final AgentToolPolicy policy = new AgentToolPolicy(new AgentProperties());

    @Test
    void projectRiskRequiresStatsAndOverdueEvidence() {
        assertFalse(policy.hasRequired(AgentSceneEnum.PROJECT_RISK, Set.of("queryTaskStats")));
        assertTrue(policy.hasRequired(AgentSceneEnum.PROJECT_RISK,
                Set.of("queryTaskStats", "queryOverdueTasks")));
    }

    @Test
    void rejectsWriteAndRepeatedTools() {
        assertThrows(BusinessException.class,
                () -> policy.requireCallAllowed(AgentSceneEnum.PROJECT_RISK, Set.of(), "deleteProject"));
        assertThrows(BusinessException.class,
                () -> policy.requireCallAllowed(AgentSceneEnum.PROJECT_RISK,
                        Set.of("queryTaskStats"), "queryTaskStats"));
    }
}
