package com.spt.learningmanage.model.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员管理权限查询事实行，包含 actor 与 target 两侧的成员关系。
 */
@Data
public class TeamMemberPermissionRow {

    private Long teamId;
    private Long teamOwnerUserId;
    private Integer teamIsDelete;
    private LocalDateTime teamDeletedAt;

    private Long actorUserId;
    private Long actorTeamMemberId;
    private String actorTeamRole;
    private Integer actorMembershipIsDelete;
    private LocalDateTime actorMembershipDeletedAt;

    private Long targetUserId;
    private Long targetTeamMemberId;
    private String targetTeamRole;
    private Integer targetMembershipIsDelete;
    private LocalDateTime targetMembershipDeletedAt;
}
