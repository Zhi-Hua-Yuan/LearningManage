package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static D4 gate; it does not claim that MySQL concurrency was executed. */
class TaskMembershipTerminationConcurrencyContractTest {

    @Test
    void d4HarnessMustCoverAllFrozenTaskMutationRaces() throws Exception {
        String source = Files.readString(Path.of(
                "src/test/java/com/spt/learningmanage/service/impl/"
                        + "TaskMembershipTerminationConcurrencyMySqlTest.java"));
        assertTrue(source.contains("createAssignedTaskVsMemberLeave"));
        assertTrue(source.contains("assignmentVsMemberLeave"));
        assertTrue(source.contains("reopenVsMemberLeave"));
        assertTrue(source.contains("reopenVsReassign"));
        assertTrue(source.contains("memberLockBlocksCreateQualification"));
        assertTrue(source.contains("memberLockBlocksAssignmentQualification"));
        assertTrue(source.contains("memberLockBlocksReopenQualification"));
        assertTrue(source.contains("team_member WHERE team_id=27001 AND user_id=17003 FOR UPDATE"));
        assertTrue(source.contains("UserHolder.remove()"));
        assertTrue(source.contains("get(10, TimeUnit.SECONDS)"));
    }

    @Test
    void d4FixtureMustBeScopedAndMustNotAdvanceFlyway() throws Exception {
        String seed = Files.readString(Path.of(
                "src/test/resources/db/stage1/wp5d4_task_membership_concurrency_seed.sql"));
        assertTrue(seed.contains("47001"));
        assertTrue(seed.contains("27001"));
        assertTrue(seed.contains("17003"));
        assertTrue(seed.contains("67004"));
        assertTrue(!seed.toLowerCase().contains("flyway"));
    }
}
