package com.spt.learningmanage.constant;

import java.util.EnumSet;
import java.util.Set;

public enum AgentRunStatusEnum {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    TIMED_OUT,
    CANCELED;

    private static final Set<AgentRunStatusEnum> TERMINAL =
            EnumSet.of(SUCCEEDED, PARTIAL, FAILED, TIMED_OUT, CANCELED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}

