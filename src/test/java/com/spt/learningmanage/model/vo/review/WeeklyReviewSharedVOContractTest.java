package com.spt.learningmanage.model.vo.review;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReviewSharedVOContractTest {

    @Test
    void teamProjection_shouldNotExposePrivateReviewFields() {
        String[] fieldNames = Arrays.stream(WeeklyReviewSharedVO.class.getDeclaredFields())
                .map(Field::getName)
                .toArray(String[]::new);

        assertTrue(Arrays.asList(fieldNames).contains("sharedSummary"));
        assertFalse(Arrays.asList(fieldNames).contains("reflection"));
        assertFalse(Arrays.asList(fieldNames).contains("nextPlan"));
        assertFalse(Arrays.asList(fieldNames).contains("taskIds"));
    }
}
