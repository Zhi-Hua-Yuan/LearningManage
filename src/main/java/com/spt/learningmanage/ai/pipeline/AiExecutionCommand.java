package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiPromptCodeEnum;

/**
 * 执行一次 AI 调用所需的公共输入。
 *
 * @param userId              当前用户 ID，用于记录调用日志
 * @param modelName           本次调用的首选模型
 * @param promptCode          提示词模板编码
 * @param userPrompt          业务场景构造的用户提示词
 * @param parseFailureMessage 响应处理失败时允许写入日志的安全信息
 */
public record AiExecutionCommand(
        Long userId,
        String modelName,
        AiPromptCodeEnum promptCode,
        String userPrompt,
        String parseFailureMessage
) {

    public AiExecutionCommand {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须为正整数");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (promptCode == null) {
            throw new IllegalArgumentException("提示词编码不能为空");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("用户提示词不能为空");
        }
        if (parseFailureMessage == null || parseFailureMessage.isBlank()) {
            throw new IllegalArgumentException("响应解析失败提示不能为空");
        }
    }
}
