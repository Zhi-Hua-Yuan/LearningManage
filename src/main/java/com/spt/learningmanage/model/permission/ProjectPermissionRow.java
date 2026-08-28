package com.spt.learningmanage.model.permission;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目权限查询事实行。
 *
 * <p>该类只承载 Mapper 返回的数据库事实，不代表 actor 已经获得任何权限。
 * 只有 PermissionService 完成规则校验后，才能生成可信的 AccessScope。</p>
 */
@Data
public class ProjectPermissionRow {

    private Long projectId;
    private Long projectOwnerUserId;
    private Long teamId;
    private Integer projectIsDelete;
    private LocalDateTime projectDeletedAt;

    private Long teamOwnerUserId;
    private Integer teamIsDelete;
    private LocalDateTime teamDeletedAt;

    private Long actorTeamMemberId;
    private String actorTeamRole;
    private Integer actorMembershipIsDelete;
    private LocalDateTime actorMembershipDeletedAt;
}
