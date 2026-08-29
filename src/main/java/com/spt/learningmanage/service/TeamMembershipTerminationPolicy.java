package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.model.entity.TeamMember;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于已加锁成员行的纯内存授权策略。
 *
 * <p>该策略不查询数据库，用于防止成员行锁定后再次依赖可能过期的普通查询。</p>
 */
@Component
public class TeamMembershipTerminationPolicy {

    public TeamMember requireLeaveAllowed(
            Long actorUserId,
            Long teamId,
            List<TeamMember> lockedMembers
    ) {
        TeamMember actor = requireMember(lockedMembers, teamId, actorUserId);
        TeamRoleEnum role = roleOf(actor);
        if (role == TeamRoleEnum.OWNER) {
            throw denied();
        }
        if (role != TeamRoleEnum.ADMIN && role != TeamRoleEnum.MEMBER) {
            throw denied();
        }
        return actor;
    }

    public TeamMember requireRemoveAllowed(
            Long actorUserId,
            Long teamId,
            Long targetUserId,
            List<TeamMember> lockedMembers
    ) {
        if (Objects.equals(actorUserId, targetUserId)) {
            throw denied();
        }

        TeamMember actor = requireMember(lockedMembers, teamId, actorUserId);
        TeamMember target = requireMember(lockedMembers, teamId, targetUserId);
        TeamRoleEnum actorRole = roleOf(actor);
        TeamRoleEnum targetRole = roleOf(target);

        if (targetRole == TeamRoleEnum.OWNER) {
            throw denied();
        }
        if (actorRole == TeamRoleEnum.OWNER) {
            if (targetRole != TeamRoleEnum.ADMIN && targetRole != TeamRoleEnum.MEMBER) {
                throw denied();
            }
            return target;
        }
        if (actorRole == TeamRoleEnum.ADMIN && targetRole == TeamRoleEnum.MEMBER) {
            return target;
        }
        throw denied();
    }

    private TeamMember requireMember(
            List<TeamMember> lockedMembers,
            Long teamId,
            Long userId
    ) {
        if (lockedMembers == null || teamId == null || userId == null) {
            throw denied();
        }

        Map<Long, TeamMember> byUserId = new HashMap<>();
        for (TeamMember member : lockedMembers) {
            if (member == null
                    || !Objects.equals(teamId, member.getTeamId())
                    || !Objects.equals(0, member.getIsDelete())
                    || member.getDeletedAt() != null
                    || member.getUserId() == null
                    || byUserId.put(member.getUserId(), member) != null) {
                throw denied();
            }
        }
        TeamMember member = byUserId.get(userId);
        if (member == null) {
            throw denied();
        }
        return member;
    }

    private TeamRoleEnum roleOf(TeamMember member) {
        TeamRoleEnum role = TeamRoleEnum.fromValue(member.getRole());
        if (role == null) {
            throw denied();
        }
        return role;
    }

    private PermissionDeniedException denied() {
        return new PermissionDeniedException();
    }
}
