package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AiExecutionContractTest {

    @Test
    void command_shouldAcceptValidValues() {
        AiExecutionCommand command = validCommand();

        Assertions.assertEquals(1L, command.userId());
        Assertions.assertEquals("qwen-test", command.modelName());
        Assertions.assertEquals(AiPromptCodeEnum.TODAY_ORDER_DEFAULT, command.promptCode());
        Assertions.assertEquals("请给出今日任务顺序", command.userPrompt());
        Assertions.assertEquals("AI 今日任务排序结果格式异常", command.parseFailureMessage());
        Assertions.assertNull(command.traceId());
    }

    @Test
    void command_shouldRejectInvalidValuesWithChineseMessages() {
        assertIllegalArgument(
                "用户 ID 必须为正整数",
                () -> new AiExecutionCommand(0L, "qwen-test", AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                        "用户提示词", "解析失败")
        );
        assertIllegalArgument(
                "模型名称不能为空",
                () -> new AiExecutionCommand(1L, " ", AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                        "用户提示词", "解析失败")
        );
        assertIllegalArgument(
                "提示词编码不能为空",
                () -> new AiExecutionCommand(1L, "qwen-test", null,
                        "用户提示词", "解析失败")
        );
        assertIllegalArgument(
                "用户提示词不能为空",
                () -> new AiExecutionCommand(1L, "qwen-test", AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                        " ", "解析失败")
        );
        assertIllegalArgument(
                "响应解析失败提示不能为空",
                () -> new AiExecutionCommand(1L, "qwen-test", AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                        "用户提示词", " ")
        );
        assertIllegalArgument(
                "traceId 格式不合法",
                () -> new AiExecutionCommand(1L, "qwen-test", AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                        "用户提示词", "解析失败", "含 空格")
        );
    }

    @Test
    void result_shouldAcceptMissingCallLogId() {
        AiExecutionResult<String> result = new AiExecutionResult<>(
                "排序结果",
                null,
                "qwen-test",
                0,
                120L
        );

        Assertions.assertEquals("排序结果", result.data());
        Assertions.assertNull(result.callLogId());
        Assertions.assertEquals("qwen-test", result.actualModel());
        Assertions.assertEquals(0, result.retryCount());
        Assertions.assertEquals(120L, result.costTimeMs());
    }

    @Test
    void result_shouldRejectInvalidValuesWithChineseMessages() {
        assertIllegalArgument(
                "AI 执行结果数据不能为空",
                () -> new AiExecutionResult<>(null, 10L, "qwen-test", 0, 1L)
        );
        assertIllegalArgument(
                "实际模型名称不能为空",
                () -> new AiExecutionResult<>("结果", 10L, " ", 0, 1L)
        );
        assertIllegalArgument(
                "重试次数不能为负数",
                () -> new AiExecutionResult<>("结果", 10L, "qwen-test", -1, 1L)
        );
        assertIllegalArgument(
                "重试次数不能为负数",
                () -> new AiExecutionResult<>("结果", 10L, "qwen-test", null, 1L)
        );
        assertIllegalArgument(
                "执行耗时不能为负数",
                () -> new AiExecutionResult<>("结果", 10L, "qwen-test", 0, -1L)
        );
    }

    @Test
    void responseProcessingException_shouldRetainChineseSafeMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("原始解析异常");

        AiResponseProcessingException exception = new AiResponseProcessingException(
                "AI 今日任务排序结果格式异常",
                cause
        );

        Assertions.assertEquals("AI 今日任务排序结果格式异常", exception.getSafeMessage());
        Assertions.assertEquals("AI 响应处理失败：AI 今日任务排序结果格式异常", exception.getMessage());
        Assertions.assertSame(cause, exception.getCause());
        Assertions.assertEquals(AiCallFailureTypeEnum.RESPONSE_PARSE, exception.getFailureType());
    }

    @Test
    void responseProcessingException_shouldSupportBusinessValidationType() {
        AiResponseProcessingException exception = AiResponseProcessingException.businessValidation(
                "AI 业务校验失败", new IllegalStateException("越权 ID")
        );

        Assertions.assertEquals(AiCallFailureTypeEnum.BUSINESS_VALIDATION, exception.getFailureType());
    }

    @Test
    void responseProcessingException_shouldRejectBlankSafeMessage() {
        assertIllegalArgument(
                "安全错误信息不能为空",
                () -> new AiResponseProcessingException(" ", new IllegalStateException("解析失败"))
        );
    }

    @Test
    void completionCommand_shouldEnforceFallbackAndDegradationSemantics() {
        assertIllegalArgument(
                "模型回退原因不能为空",
                () -> completion(true, null, false, null, null)
        );
        assertIllegalArgument(
                "规则降级原因不能为空",
                () -> completion(false, null, true, AiCallFailureTypeEnum.TIMEOUT, null)
        );
        assertIllegalArgument(
                "正常成功不能携带失败类型",
                () -> completion(false, null, false, AiCallFailureTypeEnum.INTERNAL, null)
        );
        assertIllegalArgument(
                "TIMEOUT 失败类型必须使用超时终态",
                () -> terminalCompletion(AiCallLogStatusEnum.FAILED, AiCallFailureTypeEnum.TIMEOUT)
        );
        assertIllegalArgument(
                "协议、解析或业务校验失败必须使用解析失败终态",
                () -> terminalCompletion(AiCallLogStatusEnum.FAILED, AiCallFailureTypeEnum.RESPONSE_PARSE)
        );
    }

    private AiCallLogCompletionCommand terminalCompletion(AiCallLogStatusEnum status,
                                                           AiCallFailureTypeEnum failureType) {
        return new AiCallLogCompletionCommand(
                1L, status, null, "failed", 1L,
                "requested", "actual", 0, null, null, null,
                false, null, "trace", failureType, false, null
        );
    }

    private AiCallLogCompletionCommand completion(boolean modelFallbackUsed,
                                                   com.spt.learningmanage.constant.AiFailureTypeEnum fallbackReason,
                                                   boolean degraded,
                                                   AiCallFailureTypeEnum failureType,
                                                   String degradationReason) {
        return new AiCallLogCompletionCommand(
                1L, AiCallLogStatusEnum.SUCCESS, "response", null, 1L,
                "requested", "actual", 0, "stop", null, null,
                modelFallbackUsed, fallbackReason, "trace", failureType,
                degraded, degradationReason
        );
    }

    private AiExecutionCommand validCommand() {
        return new AiExecutionCommand(
                1L,
                "qwen-test",
                AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                "请给出今日任务顺序",
                "AI 今日任务排序结果格式异常"
        );
    }

    private void assertIllegalArgument(String expectedMessage, Executable executable) {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                executable
        );
        Assertions.assertEquals(expectedMessage, exception.getMessage());
    }
}
