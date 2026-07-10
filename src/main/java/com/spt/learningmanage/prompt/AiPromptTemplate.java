package com.spt.learningmanage.prompt;

/**
 * 解析后的 Prompt 模板。
 *
 * 当前由内置模板提供；后续接入数据库后，仍使用该对象向业务层返回模板信息。
 */
public record AiPromptTemplate(
        String code,
        String scene,
        Integer version,
        String systemPrompt
) {
}
