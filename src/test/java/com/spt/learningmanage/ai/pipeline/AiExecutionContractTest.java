package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.exception.AiResponseProcessingException;
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
    }

    @Test
    void responseProcessingException_shouldRejectBlankSafeMessage() {
        assertIllegalArgument(
                "安全错误信息不能为空",
                () -> new AiResponseProcessingException(" ", new IllegalStateException("解析失败"))
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
