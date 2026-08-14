package com.spt.learningmanage.ai.pipeline;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInvocationPipelineTest {

    private static final Long USER_ID = 1L;
    private static final Long CALL_LOG_ID = 100L;
    private static final String PRIMARY_MODEL = "primary-model";
    private static final String ACTUAL_MODEL = "fallback-model";
    private static final String SYSTEM_PROMPT = "你是今日任务排序助手";
    private static final String USER_PROMPT = "请对今日任务进行排序";
    private static final String RAW_RESPONSE = "{\"items\":[]}";
    private static final String PROCESSED_RESULT = "已解析结果";
    private static final String PARSE_FAILURE_MESSAGE = "AI 今日任务排序结果格式异常";

    @Mock
    private PromptTemplateResolver promptTemplateResolver;
    @Mock
    private AiModelClient aiModelClient;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private AiResponseProcessor<String> responseProcessor;

    private AiInvocationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new AiInvocationPipeline(
                promptTemplateResolver,
                aiModelClient,
                aiCallLogService
        );
    }

    @Test
    void execute_shouldReturnProcessedResultAndCompleteCallLog() {
        stubSuccessfulInvocation();

        AiExecutionResult<String> result = pipeline.execute(command(), responseProcessor);

        Assertions.assertEquals(PROCESSED_RESULT, result.data());
        Assertions.assertEquals(CALL_LOG_ID, result.callLogId());
        Assertions.assertEquals(ACTUAL_MODEL, result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
        Assertions.assertTrue(result.costTimeMs() >= 0);

        ArgumentCaptor<AiCallLogCreateCommand> logCommandCaptor =
                ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        verify(aiCallLogService).createRunningLog(logCommandCaptor.capture());
        AiCallLogCreateCommand logCommand = logCommandCaptor.getValue();
        Assertions.assertEquals(USER_ID, logCommand.userId());
        Assertions.assertEquals("today-order", logCommand.scene());
        Assertions.assertEquals(PRIMARY_MODEL, logCommand.modelName());
        Assertions.assertEquals("today-order.default", logCommand.promptCode());
        Assertions.assertEquals(10L, logCommand.promptTemplateId());
        Assertions.assertEquals(2, logCommand.promptVersion());
        Assertions.assertEquals("database", logCommand.promptSource());
        Assertions.assertEquals(0, logCommand.retryCount());

        JSONObject requestText = JSONUtil.parseObj(logCommand.requestText());
        Assertions.assertEquals(SYSTEM_PROMPT, requestText.getStr("systemPrompt"));
        Assertions.assertEquals(USER_PROMPT, requestText.getStr("userPrompt"));

        verify(aiCallLogService).updateExecutionMetadata(CALL_LOG_ID, ACTUAL_MODEL, 1);
        verify(aiCallLogService).markSuccess(eq(CALL_LOG_ID), eq(RAW_RESPONSE), anyLong());
        verify(aiCallLogService, never()).markFailed(anyLong(), anyString(), anyLong());
        verify(aiCallLogService, never()).markTimeout(anyLong(), anyString(), anyLong());
        verify(aiCallLogService, never()).markParseFailed(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void execute_shouldPropagatePromptResolutionFailureWithoutStartingInvocation() {
        BusinessException promptFailure = new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "提示词解析失败"
        );
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT))
                .thenThrow(promptFailure);

        BusinessException actual = Assertions.assertThrows(
                BusinessException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertSame(promptFailure, actual);
        verifyNoInteractions(aiModelClient, aiCallLogService, responseProcessor);
    }

    @Test
    void execute_shouldMarkTimeoutAndRethrowInvocationException() {
        stubBeforeModelInvocation();
        AiInvocationException timeout = invocationFailure(
                AiFailureTypeEnum.TIMEOUT,
                ACTUAL_MODEL,
                1,
                "AI 服务响应超时，请稍后重试"
        );
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenThrow(timeout);

        AiInvocationException actual = Assertions.assertThrows(
                AiInvocationException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertSame(timeout, actual);
        verify(aiCallLogService).updateExecutionMetadata(CALL_LOG_ID, ACTUAL_MODEL, 1);
        verify(aiCallLogService).markTimeout(
                eq(CALL_LOG_ID),
                eq("AI 服务响应超时，请稍后重试"),
                anyLong()
        );
        verify(responseProcessor, never()).process(anyString());
    }

    @Test
    void execute_shouldMarkFailedForOrdinaryInvocationFailure() {
        stubBeforeModelInvocation();
        AiInvocationException rejected = invocationFailure(
                AiFailureTypeEnum.UPSTREAM_REJECTED,
                PRIMARY_MODEL,
                0,
                "AI 请求被上游服务拒绝"
        );
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenThrow(rejected);

        AiInvocationException actual = Assertions.assertThrows(
                AiInvocationException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertSame(rejected, actual);
        verify(aiCallLogService).markFailed(
                eq(CALL_LOG_ID),
                eq("AI 请求被上游服务拒绝"),
                anyLong()
        );
        verify(responseProcessor, never()).process(anyString());
    }

    @Test
    void execute_shouldMarkParseFailedForInvalidProviderResponse() {
        stubBeforeModelInvocation();
        AiInvocationException invalidResponse = invocationFailure(
                AiFailureTypeEnum.INVALID_RESPONSE,
                ACTUAL_MODEL,
                1,
                "AI 返回结果格式异常，请重试"
        );
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenThrow(invalidResponse);

        AiInvocationException actual = Assertions.assertThrows(
                AiInvocationException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertSame(invalidResponse, actual);
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(null),
                eq("AI 返回结果格式异常，请重试"),
                anyLong()
        );
        verify(responseProcessor, never()).process(anyString());
    }

    @Test
    void execute_shouldWrapUnexpectedModelExceptionWithChineseMessage() {
        stubBeforeModelInvocation();
        IllegalStateException unexpected = new IllegalStateException("底层客户端异常");
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenThrow(unexpected);

        AiInvocationException actual = Assertions.assertThrows(
                AiInvocationException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertEquals(AiFailureTypeEnum.INTERNAL_ERROR, actual.getFailureType());
        Assertions.assertEquals(PRIMARY_MODEL, actual.getModelName());
        Assertions.assertEquals(0, actual.getRetryCount());
        Assertions.assertEquals("AI 服务暂时不可用，请稍后重试", actual.getSafeMessage());
        Assertions.assertTrue(actual.getMessage().startsWith("AI 调用发生未分类异常"));
        Assertions.assertSame(unexpected, actual.getCause());
        verify(aiCallLogService).markFailed(
                eq(CALL_LOG_ID),
                eq("AI 服务暂时不可用，请稍后重试"),
                anyLong()
        );
        verify(responseProcessor, never()).process(anyString());
    }

    @Test
    void execute_shouldWrapProcessorFailureAndMarkParseFailed() {
        stubBeforeResponseProcessing();
        IllegalArgumentException parseFailure = new IllegalArgumentException("缺少 items 字段");
        when(responseProcessor.process(RAW_RESPONSE)).thenThrow(parseFailure);

        AiResponseProcessingException actual = Assertions.assertThrows(
                AiResponseProcessingException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertEquals(PARSE_FAILURE_MESSAGE, actual.getSafeMessage());
        Assertions.assertSame(parseFailure, actual.getCause());
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(RAW_RESPONSE),
                eq(PARSE_FAILURE_MESSAGE),
                anyLong()
        );
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
    }

    @Test
    void execute_shouldTreatNullProcessorResultAsResponseProcessingFailure() {
        stubBeforeResponseProcessing();
        when(responseProcessor.process(RAW_RESPONSE)).thenReturn(null);

        AiResponseProcessingException actual = Assertions.assertThrows(
                AiResponseProcessingException.class,
                () -> pipeline.execute(command(), responseProcessor)
        );

        Assertions.assertEquals(PARSE_FAILURE_MESSAGE, actual.getSafeMessage());
        Assertions.assertNotNull(actual.getCause());
        Assertions.assertEquals("AI 响应处理结果不能为空", actual.getCause().getMessage());
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(RAW_RESPONSE),
                eq(PARSE_FAILURE_MESSAGE),
                anyLong()
        );
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
    }

    @Test
    void execute_shouldContinueWhenCallLogCreationFails() {
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT))
                .thenReturn(promptTemplate());
        when(aiCallLogService.createRunningLog(any()))
                .thenThrow(new IllegalStateException("调用日志数据库不可用"));
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenReturn(new AiInvocationResult(RAW_RESPONSE, ACTUAL_MODEL, 1));
        when(responseProcessor.process(RAW_RESPONSE)).thenReturn(PROCESSED_RESULT);

        AiExecutionResult<String> result = pipeline.execute(command(), responseProcessor);

        Assertions.assertEquals(PROCESSED_RESULT, result.data());
        Assertions.assertNull(result.callLogId());
        verify(aiCallLogService, never()).updateExecutionMetadata(anyLong(), anyString(), any());
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
        verify(aiCallLogService, never()).markFailed(anyLong(), anyString(), anyLong());
        verify(aiCallLogService, never()).markTimeout(anyLong(), anyString(), anyLong());
        verify(aiCallLogService, never()).markParseFailed(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void execute_shouldReturnResultWhenLogUpdatesFail() {
        stubSuccessfulInvocation();
        doThrow(new IllegalStateException("执行元数据更新失败"))
                .when(aiCallLogService)
                .updateExecutionMetadata(CALL_LOG_ID, ACTUAL_MODEL, 1);
        doThrow(new IllegalStateException("成功状态更新失败"))
                .when(aiCallLogService)
                .markSuccess(eq(CALL_LOG_ID), eq(RAW_RESPONSE), anyLong());

        AiExecutionResult<String> result = pipeline.execute(command(), responseProcessor);

        Assertions.assertEquals(PROCESSED_RESULT, result.data());
        Assertions.assertEquals(CALL_LOG_ID, result.callLogId());
        Assertions.assertEquals(ACTUAL_MODEL, result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
    }

    private AiExecutionCommand command() {
        return new AiExecutionCommand(
                USER_ID,
                PRIMARY_MODEL,
                AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                USER_PROMPT,
                PARSE_FAILURE_MESSAGE
        );
    }

    private AiPromptTemplate promptTemplate() {
        return new AiPromptTemplate(
                10L,
                "today-order.default",
                "today-order",
                2,
                AiPromptSourceEnum.DATABASE,
                SYSTEM_PROMPT
        );
    }

    private void stubSuccessfulInvocation() {
        stubBeforeResponseProcessing();
        when(responseProcessor.process(RAW_RESPONSE)).thenReturn(PROCESSED_RESULT);
    }

    private void stubBeforeResponseProcessing() {
        stubBeforeModelInvocation();
        when(aiModelClient.invoke(PRIMARY_MODEL, SYSTEM_PROMPT, USER_PROMPT))
                .thenReturn(new AiInvocationResult(RAW_RESPONSE, ACTUAL_MODEL, 1));
    }

    private void stubBeforeModelInvocation() {
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT))
                .thenReturn(promptTemplate());
        when(aiCallLogService.createRunningLog(any())).thenReturn(CALL_LOG_ID);
    }

    private AiInvocationException invocationFailure(AiFailureTypeEnum failureType,
                                                    String modelName,
                                                    int retryCount,
                                                    String safeMessage) {
        return new AiInvocationException(
                failureType,
                modelName,
                retryCount,
                safeMessage,
                "模型调用失败：" + failureType,
                null
        );
    }
}
