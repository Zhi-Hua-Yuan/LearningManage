package com.spt.learningmanage.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static C5 gate. The real database assertions run in the MySQL integration
 * classes; this gate prevents the final acceptance record from drifting away
 * from the executable evidence.
 */
class WeeklyReviewC5AcceptanceContractTest {

    private static final Path ROOT = Path.of("src");

    @Test
    void c5EvidenceMustCoverPrivacyTransactionAndAiBoundaries() throws Exception {
        String mapper = read("test/java/com/spt/learningmanage/mapper/WeeklyReviewMapperReadPermissionMySqlTest.java");
        String service = read("test/java/com/spt/learningmanage/service/impl/WeeklyReviewServiceImplTest.java");
        String ai = read("test/java/com/spt/learningmanage/service/impl/AiServiceImplWeeklyPolishAuthorizationTest.java");
        String vo = read("test/java/com/spt/learningmanage/model/vo/review/WeeklyReviewSharedVOContractTest.java");
        String implementation = read("main/java/com/spt/learningmanage/service/impl/WeeklyReviewServiceImpl.java");

        assertTrue(mapper.contains("teamSharedPageShouldNeverReturnPrivateReviewRows"));
        assertTrue(mapper.contains("v2FixtureShouldHaveNoInvalidVisibilityOrAssociationRows"));
        assertTrue(service.contains("save_shouldLockTeamMembershipBeforeAuthorizingTeamView"));
        assertTrue(service.contains("update_shouldRejectPartialAssociationReplacement"));
        assertTrue(ai.contains("polish_shouldRejectUnauthorizedExplicitTaskBeforeModelInvocation"));
        assertTrue(ai.contains("verify(aiModelClient, never()).invoke"));
        assertTrue(vo.contains("teamProjection_jsonMustNotContainPrivateMarkers"));
        assertTrue(implementation.contains("@Transactional(rollbackFor = Exception.class)"));
        assertTrue(implementation.contains("selectActiveMembersForUpdate"));
        assertTrue(implementation.contains("replaceTaskAssociations"));
    }

    @Test
    void c5RecordMustKeepDatabaseAndContractGatesExplicit() throws Exception {
        String record = Files.readString(
                Path.of("docs/stage1/acceptance/pr6-wp6e-c5-final-acceptance-development-record-2026-08-31.md"),
                StandardCharsets.UTF_8);

        assertTrue(record.contains("MYSQL_CI_PENDING"));
        assertTrue(record.contains("Flyway"));
        assertTrue(record.contains("S1-A-006"));
        assertTrue(record.contains("S1-A-008"));
        assertTrue(record.contains("不修改 V1/V2 migration"));
        assertTrue(record.contains("CI_EXPECTED_TEST_COUNT"));
    }

    private String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }
}
