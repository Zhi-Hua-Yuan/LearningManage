package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.team.TeamOwnerTransferRequest;
import com.spt.learningmanage.model.dto.team.TeamUpdateRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.team.TeamDissolutionVO;
import com.spt.learningmanage.model.vo.team.TeamOwnershipTransferVO;
import com.spt.learningmanage.model.vo.team.TeamVO;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamLifecycleServiceImplTest {

    @Mock private TeamMapper teamMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private WeeklyReviewMapper weeklyReviewMapper;
    @Mock private PermissionService permissionService;
    @Mock private BusinessDataVersionService businessDataVersionService;
    @InjectMocks private TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        UserHolder.set(11L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void ownerAndAdminCanUpdateTeamProfile() {
        Team team = team(7L, 11L, "Old");
        when(teamMapper.selectActiveByIdForUpdate(7L)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(member(11L, TeamRoleEnum.ADMIN)));
        when(teamMapper.updateById(team)).thenReturn(1);

        TeamUpdateRequest request = new TeamUpdateRequest();
        request.setTeamId(7L);
        request.setName("  New team  ");
        request.setDescription("  New description  ");
        TeamVO result = service.updateTeam(request);

        assertEquals("New team", result.getName());
        assertEquals("New description", result.getDescription());
        assertEquals("ADMIN", result.getRole());
        verify(businessDataVersionService).incrementTeam(7L);
        verify(permissionService).requireActiveActor(11L);
    }

    @Test
    void inactiveActorCannotUpdateTeamProfile() {
        doThrow(new PermissionDeniedException()).when(permissionService).requireActiveActor(11L);
        TeamUpdateRequest request = new TeamUpdateRequest();
        request.setTeamId(7L);
        request.setName("Blocked");

        assertThrows(PermissionDeniedException.class, () -> service.updateTeam(request));

        verifyNoInteractions(teamMapper, teamMemberMapper);
    }

    @Test
    void regeneratingInviteInvalidatesThePreviousValue() {
        Team team = team(7L, 11L, "Team");
        team.setInviteCode("OLD-CODE");
        when(teamMapper.selectActiveByIdForUpdate(7L)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(member(11L, TeamRoleEnum.OWNER)));
        when(teamMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teamMapper.updateById(team)).thenReturn(1);

        String next = service.regenerateInvite(7L).getInviteCode();

        assertEquals(8, next.length());
        assertNotEquals("OLD-CODE", next);
        assertEquals(next, team.getInviteCode());
    }

    @Test
    void ownershipTransferAtomicallySwapsRolesAndOwnerId() {
        Team team = team(7L, 11L, "Team");
        TeamMember owner = member(11L, TeamRoleEnum.OWNER);
        TeamMember target = member(22L, TeamRoleEnum.MEMBER);
        when(teamMapper.selectActiveByIdForUpdate(7L)).thenReturn(team);
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L, 22L)))
                .thenReturn(List.of(owner, target));
        when(teamMemberMapper.updateById(any(TeamMember.class))).thenReturn(1);
        when(teamMapper.updateById(team)).thenReturn(1);

        TeamOwnerTransferRequest request = new TeamOwnerTransferRequest();
        request.setTeamId(7L);
        request.setTargetUserId(22L);
        TeamOwnershipTransferVO result = service.transferOwnership(request);

        assertEquals(22L, team.getOwnerId());
        assertEquals("ADMIN", owner.getRole());
        assertEquals("OWNER", target.getRole());
        assertEquals(22L, result.getNewOwnerUserId());
    }

    @Test
    void dissolutionIsBlockedByTeamBusinessData() {
        Team team = team(7L, 11L, "Team");
        TeamMember owner = member(11L, TeamRoleEnum.OWNER);
        when(teamMapper.selectActiveByIdForUpdate(7L)).thenReturn(team);
        when(teamMemberMapper.selectAllActiveMembersForUpdate(7L)).thenReturn(List.of(owner));
        when(projectMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(weeklyReviewMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.dissolveTeam(7L));

        assertEquals(ErrorCode.TEAM_HAS_BUSINESS_DATA, error.getErrorCode());
        assertTrue(error.getMessage().contains("1 个有效项目"));
    }

    @Test
    void emptyTeamIsSoftDeletedWithAllMemberships() {
        Team team = team(7L, 11L, "Team");
        List<TeamMember> members = List.of(
                member(11L, TeamRoleEnum.OWNER), member(22L, TeamRoleEnum.MEMBER));
        when(teamMapper.selectActiveByIdForUpdate(7L)).thenReturn(team);
        when(teamMemberMapper.selectAllActiveMembersForUpdate(7L)).thenReturn(members);
        when(projectMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(weeklyReviewMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(teamMemberMapper.deactivateAllActiveMemberships(any(), any())).thenReturn(2);
        when(teamMapper.deactivateTeamCas(any(), any())).thenReturn(1);

        TeamDissolutionVO result = service.dissolveTeam(7L);

        assertEquals(2, result.getRemovedMemberCount());
        verify(teamMemberMapper).deactivateAllActiveMemberships(any(), any());
        verify(teamMapper).deactivateTeamCas(any(), any());
    }

    private Team team(Long id, Long ownerId, String name) {
        Team team = new Team();
        team.setId(id);
        team.setOwnerId(ownerId);
        team.setName(name);
        team.setDescription("");
        team.setInviteCode("ABCDEFGH");
        team.setIsDelete(0);
        return team;
    }

    private TeamMember member(Long userId, TeamRoleEnum role) {
        TeamMember member = new TeamMember();
        member.setId(userId + 100L);
        member.setTeamId(7L);
        member.setUserId(userId);
        member.setRole(role.getValue());
        member.setIsDelete(0);
        return member;
    }
}
