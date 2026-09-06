package com.spt.learningmanage.mapper;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = {
        "/db/stage1/permission_mapper_v2_seed.sql",
        "/db/stage1/weekly_review_task_mapper_v2_seed.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WeeklyReviewTaskMapperMySqlTest {

    @Autowired
    private WeeklyReviewTaskMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseMustBeIsolatedV3() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.matches("(?i).*(?:_test|_ci_).*"),
                "mapper tests must use an isolated test database");
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) "
                        + "FROM flyway_schema_history WHERE success = 1", Integer.class));
    }

    @Test
    void shouldReadMultipleReviewsInStableOrderAndGuardEmptyInput() {
        List<WeeklyReviewTask> rows = mapper.selectByReviewIds(List.of(72002L, 72001L));
        assertEquals(List.of(972001L, 972002L, 972003L),
                rows.stream().map(WeeklyReviewTask::getId).toList());
        assertTrue(mapper.selectByReviewIds(List.of()).isEmpty());
    }

    @Test
    void shouldBatchInsertRelationsAndAllowSameTaskAcrossReviews() {
        WeeklyReviewTask first = relation(972101L, 72002L, 62001L);
        WeeklyReviewTask second = relation(972102L, 72003L, 62001L);
        assertEquals(2, mapper.batchInsert(List.of(first, second)));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM weekly_review_task WHERE id IN (972101, 972102)",
                Integer.class));
    }

    @Test
    void shouldDeleteOnlyTheRequestedReviewRelations() {
        assertEquals(2, mapper.deleteByReviewId(72001L));
        assertTrue(mapper.selectByReviewIds(List.of(72001L)).isEmpty());
        assertEquals(List.of(972003L), mapper.selectByReviewIds(List.of(72002L))
                .stream().map(WeeklyReviewTask::getId).toList());
    }

    @Test
    void shouldExposeUniqueReviewTaskConstraint() {
        WeeklyReviewTask duplicate = relation(972104L, 72001L, 62001L);
        assertThrows(DuplicateKeyException.class, () -> mapper.batchInsert(List.of(duplicate)));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM weekly_review_task WHERE weekly_review_id = 72001",
                Integer.class));
    }

    private static WeeklyReviewTask relation(Long id, Long reviewId, Long taskId) {
        WeeklyReviewTask relation = new WeeklyReviewTask();
        relation.setId(id);
        relation.setWeeklyReviewId(reviewId);
        relation.setTaskId(taskId);
        relation.setCreateTime(LocalDateTime.of(2026, 1, 7, 10, 0));
        return relation;
    }
}
