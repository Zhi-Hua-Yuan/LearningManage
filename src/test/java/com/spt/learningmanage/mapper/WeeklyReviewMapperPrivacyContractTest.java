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
        assertTrue(xml.contains("wr.is_delete = 0"));
        assertTrue(xml.contains("u.is_delete = 0"));
        assertFalse(xml.contains("wr.reflection"));
        assertFalse(xml.contains("wr.next_plan"));
        assertFalse(xml.contains("weekly_review_task"));
    }
}
