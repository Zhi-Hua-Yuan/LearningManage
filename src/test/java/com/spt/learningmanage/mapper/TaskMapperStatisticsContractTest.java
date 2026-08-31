package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskMapperStatisticsContractTest {

    private static final Path XML = Path.of("src/main/resources/mapper/TaskMapper.xml");

    @Test
    void weeklyStatisticsShouldUseAssigneeAndHalfOpenCompletionWindow() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8).toLowerCase();
        assertTrue(xml.contains("id=\"countweeklycompletedtasksbyassignee\""));
        assertTrue(xml.contains("id=\"selectweeklyfocusprojectbyassignee\""));
        assertTrue(xml.contains("t.assignee_user_id = #{assigneeuserid}"));
        assertTrue(xml.contains("t.completed_at &gt;= #{startdatetime}"));
        assertTrue(xml.contains("t.completed_at &lt; #{enddatetimeexclusive}"));
        assertTrue(xml.contains("t.is_delete = 0"));
        assertTrue(xml.contains("order by completed_count desc, t.project_id asc"));
        assertFalse(xml.contains("t.user_id = #{assigneeuserid}"));
    }
}
