package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum TaskStatusEnum {
    TODO(0, "待办"),
    IN_PROGRESS(1, "进行中"),
    DONE(2, "完成");

    private final int value;
    private final String text;

    TaskStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public static void fromValue(int value) {
        for (TaskStatusEnum status : values()) {
            if (status.value == value) {
                return;
            }
        }
        throw new IllegalArgumentException("任务状态不合法: " + value);
    }
}