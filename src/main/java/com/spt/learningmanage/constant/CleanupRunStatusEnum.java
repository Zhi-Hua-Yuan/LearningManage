package com.spt.learningmanage.constant;

import java.util.EnumSet;
import java.util.Set;

public enum CleanupRunStatusEnum {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELED;

    private static final Set<CleanupRunStatusEnum> TERMINAL =
            EnumSet.of(SUCCEEDED, PARTIAL, FAILED, CANCELED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
