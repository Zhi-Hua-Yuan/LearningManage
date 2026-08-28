package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * 阶段 1 冻结的资源权限动作。
 *
 * <p>动作只供服务端内部授权决策和审计使用，不接受客户端直接提交，也不
 * 作为数据库中的用户权限值。{@link #TASK_CREATE} 是任务视角的兼容别名，
 * 其规范决策动作是 {@link #PROJECT_CREATE_TASK}。</p>
 */
@Getter
public enum PermissionActionEnum {

    PROJECT_VIEW("PROJECT_VIEW"),
    PROJECT_CREATE_TASK("PROJECT_CREATE_TASK"),
    PROJECT_UPDATE("PROJECT_UPDATE"),
    PROJECT_ARCHIVE("PROJECT_ARCHIVE"),
    PROJECT_DELETE("PROJECT_DELETE"),
    PROJECT_MEMBER_LIST("PROJECT_MEMBER_LIST"),

    TASK_CREATE("TASK_CREATE"),
    TASK_VIEW("TASK_VIEW"),
    TASK_EDIT_CONTENT("TASK_EDIT_CONTENT"),
    TASK_CHANGE_STATUS("TASK_CHANGE_STATUS"),
    TASK_REORGANIZE("TASK_REORGANIZE"),
    TASK_ASSIGN("TASK_ASSIGN"),
    TASK_DELETE("TASK_DELETE"),
    TASK_ASSIGNMENT_HISTORY_VIEW("TASK_ASSIGNMENT_HISTORY_VIEW"),

    TEAM_MEMBER_ROLE_UPDATE("TEAM_MEMBER_ROLE_UPDATE"),
    TEAM_MEMBER_REMOVE("TEAM_MEMBER_REMOVE"),
    TEAM_LEAVE("TEAM_LEAVE"),

    REVIEW_FULL_VIEW("REVIEW_FULL_VIEW"),
    REVIEW_UPDATE("REVIEW_UPDATE"),
    REVIEW_DELETE("REVIEW_DELETE"),
    PRIVATE_REVIEW_DISCOVER("PRIVATE_REVIEW_DISCOVER"),
    TEAM_SUMMARY_VIEW("TEAM_SUMMARY_VIEW");

    private final String value;

    PermissionActionEnum(String value) {
        this.value = value;
    }

    /**
     * 将矩阵中的任务创建动作归一到项目创建任务动作，避免两套授权规则漂移。
     */
    public PermissionActionEnum canonical() {
        return this == TASK_CREATE ? PROJECT_CREATE_TASK : this;
    }
}
