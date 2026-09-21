package com.spt.learningmanage.agent;

import com.spt.learningmanage.constant.AgentSceneEnum;

import java.util.Set;

public interface AgentTool<A> {
    String name();

    /**
     * Stable, provider-facing description of the read-only capability.
     * Implementations may override this when the Java name is not descriptive
     * enough for the model.  The default keeps third-party/test tools
     * source-compatible with the registry.
     */
    default String description() {
        return name();
    }

    Set<AgentSceneEnum> allowedScenes();

    Class<A> argumentType();

    Object execute(ToolExecutionContext context, A arguments);
}
