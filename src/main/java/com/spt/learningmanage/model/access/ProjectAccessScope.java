package com.spt.learningmanage.model.access;

import com.spt.learningmanage.constant.TeamRoleEnum;

/**
 * Trusted project authorization context calculated from server-side records.
 */
public record ProjectAccessScope(
        Long projectId,
        Long ownerUserId,
        Long teamId,
        TeamRoleEnum teamRole,
        boolean projectOwner,
        boolean canView,
        boolean canManage) {

    public boolean isPersonal() {
        return teamId == null;
    }
}
