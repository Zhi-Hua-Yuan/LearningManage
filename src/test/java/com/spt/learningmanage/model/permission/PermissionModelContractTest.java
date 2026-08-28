package com.spt.learningmanage.model.permission;

import com.spt.learningmanage.constant.TeamRoleEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionModelContractTest {

    @Test
    void taskPermissionRowShouldDistinguishCreatorAndAssignee() {
        TaskPermissionRow row = new TaskPermissionRow();
        row.setTaskCreatorUserId(10L);
        row.setAssigneeUserId(20L);

        assertEquals(10L, row.getTaskCreatorUserId());
        assertEquals(20L, row.getAssigneeUserId());
    }

    @Test
    void actorPermissionRowShouldExposeOnlyAuthorizationFacts() {
        Set<String> fields = Arrays.stream(ActorPermissionRow.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("actorUserId", "actorSystemRole", "actorIsDelete"), fields);
    }

    @Test
    void weeklyReviewPermissionRowShouldNotContainPrivateContentFields() {
        Set<String> fields = Arrays.stream(WeeklyReviewPermissionRow.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertFalse(fields.contains("reflection"));
        assertFalse(fields.contains("nextPlan"));
        assertFalse(fields.contains("sharedSummary"));
        assertFalse(fields.contains("password"));
        assertFalse(fields.contains("token"));
    }

    @Test
    void personalScopeShouldNotCarryTeamRole() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectAccessScope(
                10L, 100L, 10L, null, TeamRoleEnum.MEMBER
        ));
    }

    @Test
    void teamScopeMustCarryTeamRole() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectAccessScope(
                10L, 100L, 10L, 200L, null
        ));
    }

    @Test
    void scopeShouldCalculateManagementWithoutSystemRoleBypass() {
        ProjectAccessScope owner = new ProjectAccessScope(
                10L, 100L, 10L, null, null
        );
        ProjectAccessScope member = new ProjectAccessScope(
                20L, 101L, 10L, 200L, TeamRoleEnum.MEMBER
        );
        ProjectAccessScope admin = new ProjectAccessScope(
                21L, 101L, 10L, 200L, TeamRoleEnum.ADMIN
        );

        assertTrue(owner.isPersonalProject());
        assertTrue(owner.canManage());
        assertFalse(member.canManage());
        assertTrue(admin.canManage());
    }
}
