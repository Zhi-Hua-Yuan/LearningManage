package com.spt.learningmanage.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeeklyReviewVisibilityScopeEnumTest {

    @Test
    void fromValue_shouldAcceptOnlySupportedScopes() {
        assertEquals(WeeklyReviewVisibilityScopeEnum.PRIVATE,
                WeeklyReviewVisibilityScopeEnum.fromValue("PRIVATE"));
        assertEquals(WeeklyReviewVisibilityScopeEnum.TEAM,
                WeeklyReviewVisibilityScopeEnum.fromValue("TEAM"));
        assertNull(WeeklyReviewVisibilityScopeEnum.fromValue("public"));
        assertNull(WeeklyReviewVisibilityScopeEnum.fromValue(null));
    }
}
