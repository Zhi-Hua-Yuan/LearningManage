package com.spt.learningmanage.prompt;

import com.spt.learningmanage.constant.AiPromptSourceEnum;

/**
 * 解析后的 Prompt 模板。
 *
 * 可由数据库模板或内置默认模板提供。
 */
public record AiPromptTemplate(
        Long templateId,
        String code,
        String scene,
        Integer version,
        AiPromptSourceEnum source,
        String systemPrompt
) {
}
