package com.spt.learningmanage.model.permission;

import com.spt.learningmanage.constant.TeamRoleEnum;

import java.util.Objects;

/**
 * PermissionService 校验后的项目访问上下文。
 *
 * <p>该对象是不可变的可信事实，不允许用它表达团队外用户或无效团队成员。</p>
 */
public record ProjectAccessScope(
        Long actorUserId,
        Long projectId,
        Long projectOwnerUserId,
        Long teamId,
        TeamRoleEnum teamRole
) {

    public ProjectAccessScope {
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(projectOwnerUserId, "projectOwnerUserId");

        if (teamId == null && teamRole != null) {
            throw new IllegalArgumentException("个人项目不能携带团队角色");
        }
        if (teamId != null && teamRole == null) {
            throw new IllegalArgumentException("团队项目可信范围必须携带团队角色");
        }
    }

    public boolean isPersonalProject() {
        return teamId == null;
    }

    public boolean isTeamProject() {
        return teamId != null;
    }

    public boolean canManage() {
        if (isPersonalProject()) {
            return actorUserId.equals(projectOwnerUserId);
        }
        return teamRole == TeamRoleEnum.OWNER || teamRole == TeamRoleEnum.ADMIN;
    }
}
