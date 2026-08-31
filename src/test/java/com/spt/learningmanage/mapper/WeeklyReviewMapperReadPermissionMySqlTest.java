package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.vo.review.WeeklyReviewSharedVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/db/stage1/permission_mapper_v2_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WeeklyReviewMapperReadPermissionMySqlTest {

    @Autowired
    private WeeklyReviewMapper weeklyReviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void teamSharedProjectionShouldReturnActiveFocusProjectSummary() {
        WeeklyReviewSharedVO review = weeklyReviewMapper
                .selectTeamSharedPage(new Page<>(1, 20), 22001L)
                .getRecords().stream()
                .filter(item -> item.getId().equals(72002L))
                .findFirst().orElseThrow();

        assertEquals(42002L, review.getFocusProject().getId());
        assertEquals("WP4B Active Team Project", review.getFocusProject().getName());
    }

    @Test
    void teamSharedProjectionShouldHideDeletedFocusProject() {
        jdbcTemplate.update("UPDATE project SET is_delete = 1, deleted_at = CURRENT_TIMESTAMP "
                + "WHERE id = 42002");

        WeeklyReviewSharedVO review = weeklyReviewMapper
                .selectTeamSharedPage(new Page<>(1, 20), 22001L)
                .getRecords().stream()
                .filter(item -> item.getId().equals(72002L))
                .findFirst().orElseThrow();

        assertNull(review.getFocusProject());
    }

    @Test
    void teamSharedPageShouldNeverReturnPrivateReviewRows() {
        var rows = weeklyReviewMapper.selectTeamSharedPage(new Page<>(1, 20), 22001L)
                .getRecords();

        assertFalse(rows.isEmpty());
        assertFalse(rows.stream().map(WeeklyReviewSharedVO::getId).toList().contains(72001L));
        assertEquals(2, rows.size());
    }

    @Test
    void v2FixtureShouldHaveNoInvalidVisibilityOrAssociationRows() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM weekly_review "
                        + "WHERE (visibility_scope = 'PRIVATE' AND team_id IS NOT NULL) "
                        + "OR (visibility_scope = 'TEAM' "
                        + "AND (team_id IS NULL OR shared_summary IS NULL OR TRIM(shared_summary) = ''))",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM weekly_review_task wrt "
                        + "LEFT JOIN weekly_review wr ON wr.id = wrt.weekly_review_id "
                        + "LEFT JOIN task t ON t.id = wrt.task_id "
                        + "WHERE wr.id IS NULL OR t.id IS NULL",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT weekly_review_id, task_id, COUNT(*) c "
                        + "FROM weekly_review_task GROUP BY weekly_review_id, task_id HAVING c > 1) d",
                Integer.class));
    }
}
