package com.spt.learningmanage.model.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务权限查询事实行。
 *
 * <p>明确区分任务创建人与当前受理人。该类不执行授权判断，不能被直接当作
 * “可访问任务”使用。</p>
 */
@Data
public class TaskPermissionRow {

    private Long taskId;
    private Long taskCreatorUserId;
    private Long assigneeUserId;
    private Long projectId;
    private Integer taskStatus;
    private Integer taskIsDelete;
    private LocalDateTime taskDeletedAt;

    private Long projectOwnerUserId;
    private Integer projectIsDelete;
    private LocalDateTime projectDeletedAt;

    private Long teamId;
    private Long teamOwnerUserId;
    private Integer teamIsDelete;
    private LocalDateTime teamDeletedAt;

    private Long actorTeamMemberId;
    private String actorTeamRole;
    private Integer actorMembershipIsDelete;
    private LocalDateTime actorMembershipDeletedAt;
}
