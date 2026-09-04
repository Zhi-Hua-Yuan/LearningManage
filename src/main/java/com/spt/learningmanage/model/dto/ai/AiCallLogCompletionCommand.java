package com.spt.learningmanage.model.dto.ai;

import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;

/**
 * AI 调用日志的唯一终态写入命令。
 */
public record AiCallLogCompletionCommand(
        Long logId,
        AiCallLogStatusEnum status,
        String responseText,
        String errorMessage,
        Long costTimeMs,
        String requestedModel,
        String actualModel,
        Integer retryCount,
        String finishReason,
        AiUsage usage,
        String providerRequestId,
        boolean modelFallbackUsed,
        AiFailureTypeEnum modelFallbackReason,
        String traceId,
        AiCallFailureTypeEnum failureType,
        boolean degraded,
        String degradationReason
) {
    public AiCallLogCompletionCommand {
        if (logId == null || logId <= 0) {
            throw new IllegalArgumentException("日志 ID 必须为正整数");
        }
        if (status == null || status == AiCallLogStatusEnum.RUNNING) {
            throw new IllegalArgumentException("日志终态不合法");
        }
        if (costTimeMs == null || costTimeMs < 0) {
            throw new IllegalArgumentException("执行耗时不能为负数");
        }
        if (retryCount != null && retryCount < 0) {
            throw new IllegalArgumentException("重试次数不能为负数");
        }
        if (degraded && (degradationReason == null || degradationReason.isBlank())) {
            throw new IllegalArgumentException("规则降级原因不能为空");
        }
        if (!degraded && degradationReason != null) {
            throw new IllegalArgumentException("未降级调用不能携带规则降级原因");
        }
        if (modelFallbackUsed && modelFallbackReason == null) {
            throw new IllegalArgumentException("模型回退原因不能为空");
        }
        if (!modelFallbackUsed && modelFallbackReason != null) {
            throw new IllegalArgumentException("未使用模型回退时不能携带回退原因");
        }
        if (degraded && (status != AiCallLogStatusEnum.SUCCESS || failureType == null)) {
            throw new IllegalArgumentException("规则降级必须以成功终态保留原失败类型");
        }
        if (!degraded && status == AiCallLogStatusEnum.SUCCESS && failureType != null) {
            throw new IllegalArgumentException("正常成功不能携带失败类型");
        }
        if (status != AiCallLogStatusEnum.SUCCESS && failureType == null) {
            throw new IllegalArgumentException("失败终态必须携带失败类型");
        }
        if (status == AiCallLogStatusEnum.TIMEOUT && failureType != AiCallFailureTypeEnum.TIMEOUT) {
            throw new IllegalArgumentException("超时终态必须使用 TIMEOUT 失败类型");
        }
        if (!degraded && failureType == AiCallFailureTypeEnum.TIMEOUT
                && status != AiCallLogStatusEnum.TIMEOUT) {
            throw new IllegalArgumentException("TIMEOUT 失败类型必须使用超时终态");
        }
        if (status == AiCallLogStatusEnum.PARSE_FAILED
                && failureType != AiCallFailureTypeEnum.PROTOCOL
                && failureType != AiCallFailureTypeEnum.RESPONSE_PARSE
                && failureType != AiCallFailureTypeEnum.BUSINESS_VALIDATION) {
            throw new IllegalArgumentException("解析失败终态的失败类型不合法");
        }
        if (!degraded && isParseFailure(failureType)
                && status != AiCallLogStatusEnum.PARSE_FAILED) {
            throw new IllegalArgumentException("协议、解析或业务校验失败必须使用解析失败终态");
        }
    }

    private static boolean isParseFailure(AiCallFailureTypeEnum failureType) {
        return failureType == AiCallFailureTypeEnum.PROTOCOL
                || failureType == AiCallFailureTypeEnum.RESPONSE_PARSE
                || failureType == AiCallFailureTypeEnum.BUSINESS_VALIDATION;
    }
}
