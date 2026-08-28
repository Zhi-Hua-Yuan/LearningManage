package com.spt.learningmanage.constant;

import lombok.Getter;

import java.util.Objects;

/** 任务负责人变更的审计动作。 */
@Getter
public enum TaskAssignmentActionEnum {
    INITIAL_ASSIGN("INITIAL_ASSIGN"),
    ASSIGN("ASSIGN"),
    REASSIGN("REASSIGN"),
    UNASSIGN("UNASSIGN"),
    MEMBER_LEFT("MEMBER_LEFT"),
    MEMBER_REMOVED("MEMBER_REMOVED");

    private final String value;

    TaskAssignmentActionEnum(String value) {
        this.value = value;
    }

    public static TaskAssignmentActionEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskAssignmentActionEnum action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        return null;
    }

    public static TaskAssignmentActionEnum resolve(Long fromAssigneeUserId, Long toAssigneeUserId) {
        if (Objects.equals(fromAssigneeUserId, toAssigneeUserId)) {
            return null;
        }
        if (fromAssigneeUserId == null) {
            return ASSIGN;
        }
        if (toAssigneeUserId == null) {
            return UNASSIGN;
        }
        return REASSIGN;
    }
}
