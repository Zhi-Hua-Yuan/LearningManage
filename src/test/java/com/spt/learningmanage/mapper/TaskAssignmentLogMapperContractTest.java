package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAssignmentLogMapperContractTest {

    private static final Path XML = Path.of(
            "src/main/resources/mapper/TaskAssignmentLogMapper.xml"
    );

    @Test
    void mapperXmlShouldExposeTheFrozenHistoryPageQueryAndRowMap() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);

        assertTrue(xml.contains(
                "namespace=\"com.spt.learningmanage.mapper.TaskAssignmentLogMapper\""));
        assertTrue(xml.contains("id=\"TaskAssignmentHistoryRowMap\""));
        assertTrue(xml.contains("type=\"com.spt.learningmanage.model.query.task.TaskAssignmentHistoryRow\""));
        assertTrue(xml.contains("id=\"selectAssignmentHistoryPage\""));

        List<String> columns = List.of(
                "id", "task_id", "action",
                "from_assignee_user_id", "from_assignee_username",
                "to_assignee_user_id", "to_assignee_username",
                "assigned_by_user_id", "assigned_by_username",
                "reason", "create_time"
        );
        for (String column : columns) {
            assertTrue(xml.contains("column=\"" + column + "\"")
                            || xml.contains("AS " + column),
                    "missing frozen history column: " + column);
        }
    }

    @Test
    void historyQueryShouldUseBoundTaskIdAndFrozenStableOrdering() throws Exception {
        String query = selectBody();
        String lower = query.toLowerCase();

        assertTrue(lower.contains("where log_entry.task_id = #{taskid}"));
        assertTrue(lower.contains("order by")
                        && lower.contains("log_entry.create_time desc")
                        && lower.contains("log_entry.id desc"));
        assertFalse(query.contains("${"), "history query must not interpolate SQL fragments");
        assertFalse(Pattern.compile("select\\s+\\*", Pattern.CASE_INSENSITIVE)
                .matcher(query)
                .find());
    }

    @Test
    void historyQueryShouldKeepDeletedUsersAndNullAssignmentsVisible() throws Exception {
        String query = selectBody().toLowerCase();

        assertTrue(query.contains("left join `user` from_user"));
        assertTrue(query.contains("left join `user` to_user"));
        assertTrue(query.contains("left join `user` operator_user"));
        assertTrue(query.contains("from_user.is_delete = 0"));
        assertTrue(query.contains("to_user.is_delete = 0"));
        assertTrue(query.contains("operator_user.is_delete = 0"));

        assertTrue(query.contains("log_entry.from_assignee_user_id as from_assignee_user_id"));
        assertTrue(query.contains("log_entry.to_assignee_user_id as to_assignee_user_id"));
        assertTrue(query.contains("log_entry.assigned_by_user_id as assigned_by_user_id"));
    }

    @Test
    void historyMapperShouldBeReadOnlyAndExcludePrivateUserFields() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        String lower = xml.toLowerCase();

        assertFalse(Pattern.compile("<(insert|update|delete)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(xml)
                .find());
        assertFalse(lower.contains("account"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("user_role"));
        assertFalse(lower.contains("token"));
        assertFalse(lower.contains("select user."));
        assertFalse(lower.contains("select task."));
    }

    private static String selectBody() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        int start = xml.indexOf("<select id=\"selectAssignmentHistoryPage\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start, "history select statement must exist");
        return xml.substring(start, end);
    }
}
