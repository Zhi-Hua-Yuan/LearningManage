package com.spt.learningmanage.mapper;

import com.spt.learningmanage.service.impl.TeamMembershipTerminationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static WP5-E contract for rollback boundaries and deterministic write order. */
class TeamMembershipTerminationConsistencyContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/spt/learningmanage/service/impl/TeamMembershipTerminationServiceImpl.java");
    private static final Path SEED = Path.of(
            "src/test/resources/db/stage1/wp5e_membership_transaction_seed.sql");
    private static final Path CLEANUP = Path.of(
            "src/test/resources/db/stage1/wp5e_membership_transaction_cleanup.sql");

    @Test
    void terminationEntrypointsRemainIndependentRollbackBoundaries() throws Exception {
        assertRollbackFor("leaveTeam", Long.class);
        assertRollbackFor("removeMember", com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest.class);
    }

    @Test
    void terminationWriteOrderKeepsTaskAndAuditBeforeMembershipCas() throws Exception {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8);
        assertBefore(source, "selectIncompleteAssignedTeamTasksForUpdate(",
                "bulkUnassignIncompleteTeamTasks(");
        assertBefore(source, "bulkUnassignIncompleteTeamTasks(",
                "batchInsertMembershipTerminationLogs(");
        assertBefore(source, "batchInsertMembershipTerminationLogs(",
                "deactivateMembershipCas(");
        assertTrue(source.contains("updatedCount != taskIds.size()"));
        assertTrue(source.contains("logCount != updatedCount"));
        assertTrue(source.contains("memberRows != 1"));
    }

    @Test
    void wp5eFixturesAreScopedAndContainCleanupBeforeSeedData() throws Exception {
        String seed = Files.readString(SEED, StandardCharsets.UTF_8).toLowerCase();
        String cleanup = Files.readString(CLEANUP, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(seed.contains("insert into task_assignment_log"));
        assertTrue(seed.contains("48001"));
        assertTrue(seed.contains("28001"));
        assertTrue(seed.contains("18001"));
        assertTrue(cleanup.contains("delete l"));
        assertTrue(cleanup.contains("delete i"));
        assertTrue(cleanup.contains("where t.project_id = 48001"));
        assertTrue(cleanup.contains("where id between 18001 and 18004"));
        assertTrue(!seed.contains("create table"));
        assertTrue(!seed.contains("alter table"));
        assertTrue(!seed.contains("drop table"));
        assertTrue(!cleanup.contains("drop table"));
        assertTrue(!seed.contains("${"));
        assertTrue(!cleanup.contains("${"));
    }

    private void assertRollbackFor(String methodName, Class<?> parameterType)
            throws Exception {
        Method method = TeamMembershipTerminationServiceImpl.class
                .getMethod(methodName, parameterType);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
    }

    private void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, "missing source fragment: " + first);
        assertTrue(secondIndex >= 0, "missing source fragment: " + second);
        assertTrue(firstIndex < secondIndex,
                "unexpected write order: " + first + " must precede " + second);
    }
}
