package com.spt.learningmanage.agent;

import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class AgentToolPolicy {
    private static final Map<AgentSceneEnum, Set<String>> ALLOWED = Map.of(
            AgentSceneEnum.PROJECT_RISK, Set.of(
                    "queryProjectTasks", "queryOverdueTasks", "queryTaskStats", "retrieveProjectHistory"),
            AgentSceneEnum.TEAM_WORKLOAD, Set.of(
                    "queryTeamMemberWorkload", "queryMemberOverdueTasks")
    );
    private static final Map<AgentSceneEnum, Set<String>> REQUIRED = Map.of(
            AgentSceneEnum.PROJECT_RISK, Set.of("queryTaskStats", "queryOverdueTasks"),
            AgentSceneEnum.TEAM_WORKLOAD, Set.of("queryTeamMemberWorkload", "queryMemberOverdueTasks")
    );

    private final AgentProperties properties;

    public AgentToolPolicy(AgentProperties properties) {
        this.properties = properties;
    }

    public Set<String> allowed(AgentSceneEnum scene) {
        return ALLOWED.getOrDefault(scene, Set.of());
    }

    public boolean hasRequired(AgentSceneEnum scene, Set<String> called) {
        return called.containsAll(REQUIRED.getOrDefault(scene, Set.of()));
    }

    public void requireCallAllowed(AgentSceneEnum scene,
                                   Set<String> alreadyCalled,
                                   String toolName) {
        if (!allowed(scene).contains(toolName)) {
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED);
        }
        if (alreadyCalled.contains(toolName)) {
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED, "同一 Tool 不允许重复调用");
        }
        if (alreadyCalled.size() >= properties.getMaxToolCalls()) {
            throw new BusinessException(ErrorCode.TOOL_CALL_LIMIT_EXCEEDED);
        }
    }
}
