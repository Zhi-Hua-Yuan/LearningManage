package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipCleanupMapperContractTest {

    private static final Path TEAM_MEMBER_XML = Path.of(
            "src/main/resources/mapper/TeamMemberMapper.xml");
    private static final Path TASK_XML = Path.of(
            "src/main/resources/mapper/TaskMapper.xml");
    private static final Path LOG_MAPPER = Path.of(
            "src/main/java/com/spt/learningmanage/mapper/TaskAssignmentLogMapper.java");
    private static final Path FIXTURE = Path.of(
            "src/test/resources/db/stage1/team_membership_cleanup_mapper_v2_seed.sql");

    @Test
    void teamMemberMapperMustLockActiveRowsInRelationshipIdOrder() throws Exception {
        String xml = read(TEAM_MEMBER_XML);
        String select = body(xml, "selectActiveMembersForUpdate", "select");
        String lower = select.toLowerCase();

        assertTrue(lower.contains("is_delete = 0"));
        assertTrue(lower.contains("deleted_at is null"));
        assertTrue(lower.contains("order by id asc"));
        assertTrue(lower.contains("for update"));
        assertTrue(lower.contains("and 1 = 0"));
        assertFalse(select.contains("${"));
    }

    @Test
    void membershipCasMustBindIdentityRoleAndActiveState() throws Exception {
        String xml = read(TEAM_MEMBER_XML);
        String update = body(xml, "deactivateMembershipCas", "update");
        String lower = update.toLowerCase();

        for (String fragment : new String[]{
                "id = #{membershipid}",
                "team_id = #{teamid}",
                "user_id = #{userid}",
                "role = #{expectedrole}",
                "is_delete = 0",
                "deleted_at is null",
                "set is_delete = 1"
        }) {
            assertTrue(lower.contains(fragment), "missing CAS fragment: " + fragment);
        }
        assertFalse(update.contains("${"));
    }

    @Test
    void taskMapperMustKeepFrozenCleanupScopeAndLockOrder() throws Exception {
        String xml = read(TASK_XML);
        String select = body(xml, "selectIncompleteAssignedTeamTasksForUpdate", "select");
        String update = body(xml, "bulkUnassignIncompleteTeamTasks", "update");
        String lowerSelect = select.toLowerCase();
        String lowerUpdate = update.toLowerCase();

        for (String fragment : new String[]{
                "p.team_id = #{teamid}",
                "t.assignee_user_id = #{memberuserid}",
                "t.status = 0",
                "order by t.id asc",
                "for update"
        }) {
            assertTrue(lowerSelect.contains(fragment), "missing cleanup fragment: " + fragment);
        }
        for (String forbidden : new String[]{
                "t.is_delete", "p.is_delete", "p.deleted_at", "p.status"
        }) {
            assertFalse(lowerSelect.contains(forbidden), "forbidden cleanup filter: " + forbidden);
        }
        for (String fragment : new String[]{
                "t.assignee_user_id = null",
                "t.assigned_by_user_id = #{assignedbyuserid}",
                "t.assigned_at = #{assignedat}",
                "and 1 = 0"
        }) {
            assertTrue(lowerUpdate.contains(fragment), "missing bulk update fragment: " + fragment);
        }
        assertFalse(xml.contains("${"));
    }

    @Test
    void terminationLogMapperMustBatchInsertFrozenColumns() throws Exception {
        String source = read(LOG_MAPPER);
        String lower = source.toLowerCase();

        for (String column : new String[]{
                "id", "task_id", "from_assignee_user_id", "to_assignee_user_id",
                "assigned_by_user_id", "action", "reason", "create_time"
        }) {
            assertTrue(lower.contains(column), "missing log column: " + column);
        }
        assertTrue(lower.contains("foreach"));
        assertTrue(lower.contains("@insert"));
        assertFalse(source.contains("${"));
        assertFalse(lower.contains("account"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("user_role"));
    }

    @Test
    void mysqlFixtureMustBeV2ScopedAndNonDestructive() throws Exception {
        String fixture = read(FIXTURE).toLowerCase();

        for (String table : new String[]{"`user`", "team", "team_member", "project", "task"}) {
            assertTrue(fixture.contains("insert into " + table),
                    "fixture must seed table: " + table);
        }
        assertFalse(fixture.contains("create table"));
        assertFalse(fixture.contains("alter table"));
        assertFalse(fixture.contains("drop table"));
        assertFalse(fixture.contains("delete from"));
        assertFalse(fixture.contains("${"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String body(String xml, String id, String tag) {
        String startMarker = "<" + tag + " id=\"" + id + "\"";
        int start = xml.indexOf(startMarker);
        int end = xml.indexOf("</" + tag + ">", start);
        assertTrue(start >= 0 && end > start, "missing mapper statement: " + id);
        return xml.substring(start, end);
    }
}
