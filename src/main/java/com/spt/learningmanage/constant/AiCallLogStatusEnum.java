package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum AiCallLogStatusEnum {

    RUNNING(0, "调用中"),
    SUCCESS(1, "成功"),
    FAILED(2, "调用失败"),
    PARSE_FAILED(3, "解析失败"),
    TIMEOUT(4, "超时");

    private final int value;

    private final String text;

    AiCallLogStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public static AiCallLogStatusEnum fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AiCallLogStatusEnum status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }

    public static String getText(Integer value) {
        AiCallLogStatusEnum status = fromValue(value);
        return status == null ? "未知状态" : status.getText();
    }

    public static boolean isRunning(Integer value) {
        return value != null && RUNNING.value == value;
    }

    public static boolean isSuccess(Integer value) {
        return value != null && SUCCESS.value == value;
    }

    public static boolean isFailed(Integer value) {
        return value != null && FAILED.value == value;
    }

    public static boolean isParseFailed(Integer value) {
        return value != null && PARSE_FAILED.value == value;
    }

    public static boolean isTimeout(Integer value) {
        return value != null && TIMEOUT.value == value;
    }
}
