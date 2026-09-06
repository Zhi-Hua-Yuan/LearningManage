package com.spt.learningmanage.agent;

import com.spt.learningmanage.constant.AgentSceneEnum;

import java.util.Set;

public interface AgentTool<A> {
    String name();

    Set<AgentSceneEnum> allowedScenes();

    Class<A> argumentType();

    Object execute(ToolExecutionContext context, A arguments);
}
