package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum TaskStatusEnum {
    TODO(0, "未完成"),
    DONE_BASIC(1, "一般完成"),
    DONE_STANDARD(2, "正常完成"),
    DONE_EXCELLENT(3, "超额完成");

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

    public static boolean isCompleted(Integer value) {
        return value != null && value >= DONE_BASIC.value && value <= DONE_EXCELLENT.value;
    }
}