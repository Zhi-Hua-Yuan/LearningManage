package com.spt.learningmanage.ai.pipeline;

/**
 * 一次 AI 调用经过场景响应处理后的内部执行结果。
 *
 * @param data         经过场景解析和校验的类型化结果
 * @param callLogId    AI 调用日志 ID；日志创建失败时允许为空
 * @param actualModel  实际返回结果的模型
 * @param retryCount   模型客户端实际重试次数
 * @param costTimeMs   模型调用及响应处理的总耗时
 * @param <T>          场景结果类型
 */
public record AiExecutionResult<T>(
        T data,
        Long callLogId,
        String actualModel,
        Integer retryCount,
        long costTimeMs
) {

    public AiExecutionResult {
        if (data == null) {
            throw new IllegalArgumentException("AI 执行结果数据不能为空");
        }
        if (actualModel == null || actualModel.isBlank()) {
            throw new IllegalArgumentException("实际模型名称不能为空");
        }
        if (retryCount == null || retryCount < 0) {
            throw new IllegalArgumentException("重试次数不能为负数");
        }
        if (costTimeMs < 0) {
            throw new IllegalArgumentException("执行耗时不能为负数");
        }
    }
}
