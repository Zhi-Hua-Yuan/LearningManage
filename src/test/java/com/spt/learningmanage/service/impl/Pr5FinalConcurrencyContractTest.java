package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static WP5-F gate for final termination concurrency coverage and fixture safety. */
class Pr5FinalConcurrencyContractTest {

    private static final Path TEST = Path.of(
            "src/test/java/com/spt/learningmanage/service/impl/"
                    + "TeamMembershipTerminationConcurrencyMySqlTest.java");
    private static final Path SEED = Path.of(
            "src/test/resources/db/stage1/wp5f_membership_termination_concurrency_seed.sql");
    private static final Path CLEANUP = Path.of(
            "src/test/resources/db/stage1/wp5f_membership_termination_concurrency_cleanup.sql");

    @Test
    void finalHarnessMustCoverRemovalMutationAndTerminationRaces() throws Exception {
        String source = Files.readString(TEST, StandardCharsets.UTF_8);
        assertTrue(source.contains("createAssignedTaskVsMemberRemove"));
        assertTrue(source.contains("assignmentVsMemberRemove"));
        assertTrue(source.contains("reopenVsMemberRemove"));
        assertTrue(source.contains("concurrentRemovals"));
        assertTrue(source.contains("leaveVsRemove"));
        assertTrue(source.contains("40300"));
        assertTrue(source.contains("terminationLogCount"));
        assertTrue(source.contains("UserHolder.remove()"));
        assertTrue(source.contains("get(10, TimeUnit.SECONDS)"));
    }

    @Test
    void finalFixturesMustBeIsolatedAndMustNotChangeFlyway() throws Exception {
        String seed = Files.readString(SEED, StandardCharsets.UTF_8).toLowerCase();
        String cleanup = Files.readString(CLEANUP, StandardCharsets.UTF_8).toLowerCase();
        assertTrue(seed.contains("49001"));
        assertTrue(seed.contains("29001"));
        assertTrue(seed.contains("19003"));
        assertTrue(seed.contains("69002"));
        assertTrue(cleanup.contains("where t.project_id = 49001"));
        assertTrue(cleanup.contains("where id between 19001 and 19004"));
        assertTrue(!seed.contains("create table"));
        assertTrue(!seed.contains("alter table"));
        assertTrue(!seed.contains("drop table"));
        assertTrue(!cleanup.contains("drop table"));
        assertTrue(!seed.contains("flyway"));
        assertTrue(!cleanup.contains("flyway"));
    }
}
