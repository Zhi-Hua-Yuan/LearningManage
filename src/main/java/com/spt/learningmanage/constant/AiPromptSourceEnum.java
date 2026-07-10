package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * Prompt 模板的实际来源。
 */
@Getter
public enum AiPromptSourceEnum {

    DATABASE("database"),
    BUILTIN("builtin");

    private final String code;

    AiPromptSourceEnum(String code) {
        this.code = code;
    }
}
