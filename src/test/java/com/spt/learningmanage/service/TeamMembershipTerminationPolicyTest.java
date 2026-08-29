package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.model.entity.TeamMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamMembershipTerminationPolicyTest {

    private final TeamMembershipTerminationPolicy policy =
            new TeamMembershipTerminationPolicy();

    @Test
    void leaveAllowsOnlyActiveAdminOrMember() {
        assertEquals(11L, policy.requireLeaveAllowed(11L, 7L,
                List.of(member(101L, 7L, 11L, "MEMBER"))).getUserId());
        assertEquals(12L, policy.requireLeaveAllowed(12L, 7L,
                List.of(member(102L, 7L, 12L, "ADMIN"))).getUserId());
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireLeaveAllowed(13L, 7L,
                        List.of(member(103L, 7L, 13L, "OWNER"))));
    }

    @Test
    void removeEnforcesOwnerAdminMemberMatrixAndNoSelfRemoval() {
        assertEquals(22L, policy.requireRemoveAllowed(21L, 7L, 22L,
                List.of(member(201L, 7L, 21L, "OWNER"),
                        member(202L, 7L, 22L, "ADMIN"))).getUserId());
        assertEquals(23L, policy.requireRemoveAllowed(21L, 7L, 23L,
                List.of(member(201L, 7L, 21L, "OWNER"),
                        member(203L, 7L, 23L, "MEMBER"))).getUserId());
        assertEquals(24L, policy.requireRemoveAllowed(22L, 7L, 24L,
                List.of(member(202L, 7L, 22L, "ADMIN"),
                        member(204L, 7L, 24L, "MEMBER"))).getUserId());

        assertThrows(PermissionDeniedException.class, () ->
                policy.requireRemoveAllowed(22L, 7L, 23L,
                        List.of(member(202L, 7L, 22L, "ADMIN"),
                                member(203L, 7L, 23L, "ADMIN"))));
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireRemoveAllowed(23L, 7L, 24L,
                        List.of(member(203L, 7L, 23L, "MEMBER"),
                                member(204L, 7L, 24L, "MEMBER"))));
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireRemoveAllowed(21L, 7L, 25L,
                        List.of(member(201L, 7L, 21L, "OWNER"),
                                member(205L, 7L, 25L, "OWNER"))));
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireRemoveAllowed(21L, 7L, 21L,
                        List.of(member(201L, 7L, 21L, "OWNER"))));
    }

    @Test
    void inactiveOrMalformedLockedRowsAreDeniedWithoutLeakingState() {
        TeamMember inactive = member(301L, 7L, 31L, "MEMBER");
        inactive.setIsDelete(1);
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireLeaveAllowed(31L, 7L, List.of(inactive)));

        TeamMember deletedAt = member(302L, 7L, 32L, "MEMBER");
        deletedAt.setDeletedAt(java.time.LocalDateTime.now());
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireLeaveAllowed(32L, 7L, List.of(deletedAt)));

        TeamMember wrongTeam = member(303L, 8L, 33L, "MEMBER");
        assertThrows(PermissionDeniedException.class, () ->
                policy.requireLeaveAllowed(33L, 7L, List.of(wrongTeam)));
    }

    private TeamMember member(Long id, Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setId(id);
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }
}
