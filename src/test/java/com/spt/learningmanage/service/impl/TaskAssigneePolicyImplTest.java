package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.TaskAssigneeQueryMapper;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssigneePolicyImplTest {

    @Mock
    private TaskAssigneeQueryMapper queryMapper;

    @InjectMocks
    private TaskAssigneePolicyImpl policy;

    @Test
    void personalProject_defaultsToOwner() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        Assertions.assertEquals(1L, policy.resolveInitialAssignee(scope, null));
    }

    @Test
    void personalProject_rejectsOtherAssignee() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        Assertions.assertThrows(BusinessException.class, () -> policy.resolveInitialAssignee(scope, 2L));
    }

    @Test
    void teamProject_allowsUnassigned() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.MEMBER);
        Assertions.assertNull(policy.resolveInitialAssignee(scope, null));
    }

    @Test
    void teamProject_requiresActiveMember() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.ADMIN);
        when(queryMapper.countActiveTeamAssignee(20L, 2L)).thenReturn(1);
        Assertions.assertEquals(2L, policy.resolveInitialAssignee(scope, 2L));
        verify(queryMapper).countActiveTeamAssignee(20L, 2L);
    }

    @Test
    void teamProject_rejectsInactiveMember() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.OWNER);
        when(queryMapper.countActiveTeamAssignee(20L, 2L)).thenReturn(0);
        Assertions.assertThrows(BusinessException.class, () -> policy.resolveInitialAssignee(scope, 2L));
    }

    @Test
    void assignmentTarget_allowsExplicitUnassign() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.ADMIN);
        Assertions.assertDoesNotThrow(() -> policy.validateAssignmentTarget(scope, null));
    }

    @Test
    void assignmentTarget_usesLockedActiveMemberCheck() {
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.ADMIN);
        when(queryMapper.selectActiveTeamAssigneeForUpdate(20L, 2L)).thenReturn(2L);
        Assertions.assertDoesNotThrow(() -> policy.validateAssignmentTarget(scope, 2L));
        verify(queryMapper).selectActiveTeamAssigneeForUpdate(20L, 2L);
    }
}
