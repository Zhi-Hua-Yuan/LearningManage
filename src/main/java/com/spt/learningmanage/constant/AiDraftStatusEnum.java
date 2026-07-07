package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum AiDraftStatusEnum {

    PREVIEW(0, "预览中"),
    CONFIRMED(1, "已确认"),
    CANCELED(2, "已取消"),
    EXPIRED(3, "已过期");

    private final int value;

    private final String text;

    AiDraftStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public static AiDraftStatusEnum fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AiDraftStatusEnum status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }

    public static String getText(Integer value) {
        AiDraftStatusEnum status = fromValue(value);
        return status == null ? "未知状态" : status.getText();
    }

    public static boolean isValid(Integer value) {
        return fromValue(value) != null;
    }

    public static boolean isPreview(Integer value) {
        return value != null && PREVIEW.value == value;
    }

    public static boolean isConfirmed(Integer value) {
        return value != null && CONFIRMED.value == value;
    }

    public static boolean isCanceled(Integer value) {
        return value != null && CANCELED.value == value;
    }

    public static boolean isExpired(Integer value) {
        return value != null && EXPIRED.value == value;
    }
}
