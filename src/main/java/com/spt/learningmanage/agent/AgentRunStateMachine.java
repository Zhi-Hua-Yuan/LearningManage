package com.spt.learningmanage.agent;

import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class AgentRunStateMachine {
    private static final Map<AgentRunStatusEnum, Set<AgentRunStatusEnum>> ALLOWED = Map.of(
            AgentRunStatusEnum.PENDING, Set.of(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.CANCELED),
            AgentRunStatusEnum.RUNNING, Set.of(AgentRunStatusEnum.SUCCEEDED, AgentRunStatusEnum.PARTIAL,
                    AgentRunStatusEnum.FAILED, AgentRunStatusEnum.TIMED_OUT, AgentRunStatusEnum.CANCELED)
    );

    public boolean canTransition(AgentRunStatusEnum from, AgentRunStatusEnum to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(AgentRunStatusEnum from, AgentRunStatusEnum to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.AGENT_RUN_ALREADY_FINISHED, "Agent Run 状态转换不合法");
        }
    }
}
