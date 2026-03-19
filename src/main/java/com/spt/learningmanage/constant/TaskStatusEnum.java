package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum TaskStatusEnum {
    TODO(0, "待办"),
    IN_PROGRESS(1, "进行中"),
    DONE(2, "完成");

    private final int value;
    private final String desc;

    TaskStatusEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static TaskStatusEnum fromValue(int value) {
        for (TaskStatusEnum status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status value: " + value);
    }
}
