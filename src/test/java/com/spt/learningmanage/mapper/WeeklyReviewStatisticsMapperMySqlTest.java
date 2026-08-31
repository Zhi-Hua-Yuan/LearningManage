package com.spt.learningmanage.mapper;

import com.spt.learningmanage.LearningManageApplication;
import com.spt.learningmanage.model.query.review.WeeklyReviewFocusProjectRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = LearningManageApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/db/stage1/weekly_review_statistics_seed.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WeeklyReviewStatisticsMapperMySqlTest {

    @Autowired
    private TaskMapper taskMapper;

    @Test
    void countShouldUseAssigneeAndCompletionWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 17, 0, 0);

        assertEquals(2L, taskMapper.countWeeklyCompletedTasksByAssignee(13002L, start, end));
        assertEquals(1L, taskMapper.countWeeklyCompletedTasksByAssignee(13001L, start, end));
    }

    @Test
    void focusProjectShouldUseStableCountThenIdOrdering() {
        WeeklyReviewFocusProjectRow row = taskMapper.selectWeeklyFocusProjectByAssignee(
                13002L,
                LocalDateTime.of(2026, 8, 10, 0, 0),
                LocalDateTime.of(2026, 8, 17, 0, 0));

        assertEquals(43001L, row.getProjectId());
        assertEquals("C4 Stats Project A", row.getProjectName());
        assertEquals(1, row.getCompletedCount());
    }
}
