package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum AgentSceneEnum {
    PROJECT_RISK("project-risk-report"),
    TEAM_WORKLOAD("team-workload-report");

    private final String draftScene;

    AgentSceneEnum(String draftScene) {
        this.draftScene = draftScene;
    }
}

