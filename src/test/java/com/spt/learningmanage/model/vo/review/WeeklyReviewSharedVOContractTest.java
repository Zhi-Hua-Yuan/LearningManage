package com.spt.learningmanage.model.vo.review;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Test
    void teamProjection_jsonMustNotContainPrivateMarkers() throws Exception {
        WeeklyReviewSharedVO vo = new WeeklyReviewSharedVO();
        vo.setId(72002L);
        vo.setSharedSummary("shared-marker");

        String json = new ObjectMapper().writeValueAsString(vo);

        assertTrue(json.contains("sharedSummary"));
        assertTrue(json.contains("shared-marker"));
        assertFalse(json.contains("reflection"));
        assertFalse(json.contains("nextPlan"));
        assertFalse(json.contains("taskIds"));
        assertFalse(json.contains("private-marker"));
    }
}
