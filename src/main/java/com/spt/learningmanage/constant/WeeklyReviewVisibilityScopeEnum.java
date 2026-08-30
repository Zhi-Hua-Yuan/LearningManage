package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum WeeklyReviewVisibilityScopeEnum {

    PRIVATE("PRIVATE"),
    TEAM("TEAM");

    private final String value;

    WeeklyReviewVisibilityScopeEnum(String value) {
        this.value = value;
    }

    public static WeeklyReviewVisibilityScopeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WeeklyReviewVisibilityScopeEnum scope : values()) {
            if (scope.value.equals(value)) {
                return scope;
            }
        }
        return null;
    }
}
