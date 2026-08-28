package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionQueryMapperContractTest {

    private static final Path XML = Path.of(
            "src/main/resources/mapper/PermissionQueryMapper.xml"
    );

    @Test
    void mapperXmlShouldExposeAllPermissionFactQueries() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);

        assertTrue(xml.contains("namespace=\"com.spt.learningmanage.mapper.PermissionQueryMapper\""));
        assertTrue(xml.contains("id=\"selectActorPermissionRow\""));
        assertTrue(xml.contains("id=\"selectProjectPermissionRows\""));
        assertTrue(xml.contains("id=\"selectTaskPermissionRows\""));
        assertTrue(xml.contains("id=\"selectWeeklyReviewPermissionRows\""));
        assertTrue(xml.contains("id=\"selectTeamMemberPermissionRow\""));
        assertTrue(xml.contains("<foreach"));
        assertTrue(xml.contains("task.assignee_user_id AS assignee_user_id"));
    }

    @Test
    void mapperXmlShouldBeReadOnlyAndKeepPrivateReviewContentOut() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        String lower = xml.toLowerCase();

        assertFalse(Pattern.compile("<(insert|update|delete)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(xml)
                .find());
        assertFalse(lower.contains("userholder"));
        assertFalse(lower.contains("reflection"));
        assertFalse(lower.contains("next_plan"));
        assertFalse(lower.contains("shared_summary"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("token"));
    }

    @Test
    void actorQueryShouldReturnOnlyLifecycleAndSystemRoleFacts() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        int start = xml.indexOf("id=\"selectActorPermissionRow\"");
        int end = xml.indexOf("</select>", start);
        String actorQuery = xml.substring(start, end);

        assertTrue(actorQuery.contains("u.id AS actor_user_id"));
        assertTrue(actorQuery.contains("u.user_role AS actor_system_role"));
        assertTrue(actorQuery.contains("u.is_delete AS actor_is_delete"));
        assertFalse(actorQuery.toLowerCase().contains("password"));
        assertFalse(actorQuery.toLowerCase().contains("token"));
    }
}
