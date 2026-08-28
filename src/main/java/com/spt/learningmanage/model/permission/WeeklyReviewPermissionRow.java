package com.spt.learningmanage.model.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周复盘权限查询事实行。
 *
 * <p>故意不包含 reflection、nextPlan 或 sharedSummary，权限查询层不应读取
 * 复盘正文或共享正文。</p>
 */
@Data
public class WeeklyReviewPermissionRow {

    private Long reviewId;
    private Long authorUserId;
    private String visibilityScope;
    private Long teamId;

    private Long teamOwnerUserId;
    private Integer teamIsDelete;
    private LocalDateTime teamDeletedAt;

    private Long actorTeamMemberId;
    private String actorTeamRole;
    private Integer actorMembershipIsDelete;
    private LocalDateTime actorMembershipDeletedAt;
}
