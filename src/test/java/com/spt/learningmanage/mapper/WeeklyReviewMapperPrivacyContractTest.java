package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReviewMapperPrivacyContractTest {

    @Test
    void teamQuery_shouldSelectOnlySharedProjectionAndFilterTeamScope() throws IOException {
        String xml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/WeeklyReviewMapper.xml")) {
            if (input == null) {
                throw new IOException("WeeklyReviewMapper.xml not found");
            }
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("BINARY wr.visibility_scope = BINARY 'TEAM'"));
        assertTrue(xml.contains("wr.shared_summary"));
        // weekly_review is physically deleted in V1/V2; it has no is_delete column.
        assertFalse(xml.contains("wr.is_delete"));
        assertTrue(xml.contains("u.is_delete = 0"));
        assertTrue(xml.contains("fp.team_id = wr.team_id"));
        assertTrue(xml.contains("fp.is_delete = 0"));
        assertTrue(xml.contains("fp.deleted_at IS NULL"));
        assertFalse(xml.contains("wr.reflection"));
        assertFalse(xml.contains("wr.next_plan"));
        assertFalse(xml.contains("weekly_review_task"));
    }

    @Test
    void writePath_shouldProvideExplicitReviewLocksWithoutLogicalDeletePredicate() throws IOException {
        String xml;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/WeeklyReviewMapper.xml")) {
            if (input == null) {
                throw new IOException("WeeklyReviewMapper.xml not found");
            }
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        assertTrue(xml.contains("id=\"selectbyidforupdate\""));
        assertTrue(xml.contains("id=\"selectbyuseryearweekforupdate\""));
        assertTrue(xml.contains("id=\"updateforwrite\""));
        assertTrue(xml.contains("team_id = #{teamid}"));
        assertTrue(xml.contains("focus_project_id = #{focusprojectid}"));
        assertTrue(xml.contains("shared_summary = #{sharedsummary}"));
        assertTrue(xml.contains("for update"));
        assertFalse(xml.contains("wr.is_delete"));
        assertFalse(xml.contains("select *"));
    }
}
