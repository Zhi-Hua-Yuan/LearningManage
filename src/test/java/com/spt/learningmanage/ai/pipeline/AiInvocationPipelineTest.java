package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiUsage;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInvocationPipelineTest {

    private static final long USER_ID = 1L;
    private static final long LOG_ID = 100L;
    private static final String REQUESTED_MODEL = "primary-model";
    private static final String ACTUAL_MODEL = "fallback-model";
    private static final String RAW = "{\"items\":[]}";
    private static final String TRACE_ID = "trace-wp3-001";

    @Mock private PromptTemplateResolver promptTemplateResolver;
    @Mock private AiModelClient aiModelClient;
    @Mock private AiCallLogService aiCallLogService;

    private AiInvocationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new AiInvocationPipeline(promptTemplateResolver, aiModelClient, aiCallLogService);
        lenient().when(promptTemplateResolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT)).thenReturn(template());
        lenient().when(aiCallLogService.createRunningLog(any())).thenReturn(LOG_ID);
        lenient().when(aiCallLogService.complete(any())).thenReturn(true);
    }

    @Test
    void execute_shouldUseChatAndReturnAllProtocolMetadata() {
        AiUsage usage = new AiUsage(12, 8, 20);
        when(aiModelClient.chat(any())).thenReturn(new AiChatResult(
                RAW, List.of(), "stop", usage, "req-1", REQUESTED_MODEL,
                ACTUAL_MODEL, 1, true, AiFailureTypeEnum.RATE_LIMITED
        ));

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID), raw -> "ok");

        Assertions.assertEquals("ok", result.data());
        Assertions.assertEquals(REQUESTED_MODEL, result.requestedModel());
        Assertions.assertEquals(ACTUAL_MODEL, result.actualModel());
        Assertions.assertEquals(usage, result.usage());
        Assertions.assertTrue(result.modelFallbackUsed());
        Assertions.assertFalse(result.degraded());
        Assertions.assertEquals(TRACE_ID, result.traceId());

        ArgumentCaptor<AiChatCommand> chatCaptor = ArgumentCaptor.forClass(AiChatCommand.class);
        verify(aiModelClient).chat(chatCaptor.capture());
        Assertions.assertEquals(2, chatCaptor.getValue().messages().size());
        Assertions.assertTrue(chatCaptor.getValue().tools().isEmpty());
        Assertions.assertEquals(com.spt.learningmanage.model.dto.ai.chat.AiToolChoice.Mode.NONE,
                chatCaptor.getValue().toolChoice().mode());

        ArgumentCaptor<AiCallLogCompletionCommand> completionCaptor =
                ArgumentCaptor.forClass(AiCallLogCompletionCommand.class);
        verify(aiCallLogService).complete(completionCaptor.capture());
        AiCallLogCompletionCommand completion = completionCaptor.getValue();
        Assertions.assertEquals(AiCallLogStatusEnum.SUCCESS, completion.status());
        Assertions.assertEquals("req-1", completion.providerRequestId());
        Assertions.assertEquals(20, completion.usage().totalTokens());
        Assertions.assertTrue(completion.modelFallbackUsed());
        Assertions.assertFalse(completion.degraded());
        Assertions.assertNull(completion.failureType());
    }

    @Test
    void execute_shouldGenerateTraceWhenMissing() {
        stubSuccess();

        AiExecutionResult<String> result = pipeline.execute(command(null), raw -> "ok");

        Assertions.assertTrue(result.traceId().matches("[a-f0-9]{32}"));
        ArgumentCaptor<AiCallLogCreateCommand> captor = ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        verify(aiCallLogService).createRunningLog(captor.capture());
        Assertions.assertEquals(result.traceId(), captor.getValue().traceId());
    }

    @Test
    void execute_shouldClassifyTimeoutAndKeepOriginalException() {
        AiInvocationException timeout = failure(AiFailureTypeEnum.TIMEOUT, "AI 服务响应超时");
        when(aiModelClient.chat(any())).thenThrow(timeout);

        AiInvocationException actual = Assertions.assertThrows(AiInvocationException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> "never"));

        Assertions.assertSame(timeout, actual);
        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertEquals(AiCallLogStatusEnum.TIMEOUT, completion.status());
        Assertions.assertEquals(AiCallFailureTypeEnum.TIMEOUT, completion.failureType());
        Assertions.assertFalse(completion.degraded());
    }

    @Test
    void execute_shouldClassifyUnauthorizedUpstreamAsAuth() {
        AiInvocationException unauthorized = new AiInvocationException(
                AiFailureTypeEnum.UPSTREAM_REJECTED, REQUESTED_MODEL, 0,
                "AI 服务请求被拒绝，请联系管理员", "upstream status=401", null, 401
        );
        when(aiModelClient.chat(any())).thenThrow(unauthorized);

        Assertions.assertThrows(AiInvocationException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> "never"));

        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertEquals(AiCallFailureTypeEnum.AUTH, completion.failureType());
        Assertions.assertEquals(AiCallLogStatusEnum.FAILED, completion.status());
    }

    @ParameterizedTest
    @MethodSource("modelFailureMappings")
    void execute_shouldMapModelFailures(AiFailureTypeEnum clientType,
                                        AiCallFailureTypeEnum logType) {
        when(aiModelClient.chat(any())).thenThrow(failure(clientType, "safe failure"));

        Assertions.assertThrows(AiInvocationException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> "never"));

        Assertions.assertEquals(logType, captureCompletion().failureType());
    }

    @Test
    void execute_shouldWrapUnknownModelExceptionAsInternal() {
        when(aiModelClient.chat(any())).thenThrow(new IllegalStateException("unexpected"));

        AiInvocationException actual = Assertions.assertThrows(AiInvocationException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> "never"));

        Assertions.assertEquals(AiFailureTypeEnum.INTERNAL_ERROR, actual.getFailureType());
        Assertions.assertEquals(AiCallFailureTypeEnum.INTERNAL, captureCompletion().failureType());
    }

    @Test
    void execute_shouldRejectToolOnlyResultAsProtocolFailure() {
        when(aiModelClient.chat(any())).thenReturn(new AiChatResult(
                null, List.of(AiToolCall.function("call-1", "query", "{}")),
                "tool_calls", null, "req-tool", REQUESTED_MODEL, REQUESTED_MODEL, 0, false, null
        ));

        AiInvocationException actual = Assertions.assertThrows(AiInvocationException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> "never"));

        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, actual.getFailureType());
        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertEquals(AiCallLogStatusEnum.PARSE_FAILED, completion.status());
        Assertions.assertEquals(AiCallFailureTypeEnum.PROTOCOL, completion.failureType());
    }

    @Test
    void execute_shouldDistinguishParseAndBusinessValidation() {
        stubSuccess();
        Assertions.assertThrows(AiResponseProcessingException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> { throw new IllegalArgumentException("bad json"); }));
        Assertions.assertEquals(AiCallFailureTypeEnum.RESPONSE_PARSE, captureCompletion().failureType());
    }

    @Test
    void execute_shouldClassifyBusinessExceptionAsBusinessValidation() {
        stubSuccess();
        Assertions.assertThrows(AiResponseProcessingException.class,
                () -> pipeline.execute(command(TRACE_ID), raw -> {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "unknown task id");
                }));
        Assertions.assertEquals(AiCallFailureTypeEnum.BUSINESS_VALIDATION, captureCompletion().failureType());
    }

    @Test
    void execute_shouldRecordRuleDegradationAfterModelFailure() {
        when(aiModelClient.chat(any())).thenThrow(failure(AiFailureTypeEnum.RATE_LIMITED, "请求过于频繁"));

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID), raw -> "never", failure -> "rule-result");

        Assertions.assertEquals("rule-result", result.data());
        Assertions.assertTrue(result.degraded());
        Assertions.assertFalse(result.modelFallbackUsed());
        Assertions.assertTrue(result.degradationReason().startsWith("RATE_LIMIT"));
        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertEquals(AiCallLogStatusEnum.SUCCESS, completion.status());
        Assertions.assertEquals(AiCallFailureTypeEnum.RATE_LIMIT, completion.failureType());
        Assertions.assertTrue(completion.degraded());
        Assertions.assertEquals(result.degradationReason(), completion.degradationReason());
    }

    @Test
    void execute_shouldKeepExplicitFailedModelFallbackMetadata() {
        AiInvocationException afterFallback = new AiInvocationException(
                AiFailureTypeEnum.TIMEOUT, REQUESTED_MODEL, ACTUAL_MODEL, 1,
                "AI 服务响应超时", "fallback model timeout", null,
                null, true, AiFailureTypeEnum.NETWORK_ERROR
        );
        when(aiModelClient.chat(any())).thenThrow(afterFallback);

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID),
                raw -> "never", failure -> "rule-result");

        Assertions.assertTrue(result.modelFallbackUsed());
        Assertions.assertEquals(ACTUAL_MODEL, result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertTrue(completion.modelFallbackUsed());
        Assertions.assertEquals(AiFailureTypeEnum.NETWORK_ERROR, completion.modelFallbackReason());
    }

    @Test
    void execute_shouldIncludeRuleFallbackDuration() {
        when(aiModelClient.chat(any())).thenThrow(failure(AiFailureTypeEnum.TIMEOUT, "AI 服务响应超时"));

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID), raw -> "never", failure -> {
            long deadline = System.currentTimeMillis() + 25L;
            while (System.currentTimeMillis() < deadline) {
                Thread.onSpinWait();
            }
            return "rule-result";
        });

        Assertions.assertTrue(result.costTimeMs() >= 20L);
        Assertions.assertTrue(captureCompletion().costTimeMs() >= 20L);
    }

    @Test
    void execute_shouldPreserveModelFallbackWhenRuleDegradationAlsoOccurs() {
        when(aiModelClient.chat(any())).thenReturn(new AiChatResult(
                "not-json", List.of(), "stop", null, "req-2", REQUESTED_MODEL,
                ACTUAL_MODEL, 1, true, AiFailureTypeEnum.NETWORK_ERROR
        ));

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID),
                raw -> { throw new IllegalArgumentException("bad json"); }, failure -> "rule-result");

        Assertions.assertTrue(result.modelFallbackUsed());
        Assertions.assertTrue(result.degraded());
        AiCallLogCompletionCommand completion = captureCompletion();
        Assertions.assertTrue(completion.modelFallbackUsed());
        Assertions.assertTrue(completion.degraded());
        Assertions.assertEquals(AiCallFailureTypeEnum.RESPONSE_PARSE, completion.failureType());
    }

    @Test
    void execute_shouldContinueWhenLogOperationsFail() {
        when(aiCallLogService.createRunningLog(any())).thenThrow(new IllegalStateException("db down"));
        stubSuccess();

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID), raw -> "ok");

        Assertions.assertEquals("ok", result.data());
        Assertions.assertNull(result.callLogId());
        verify(aiCallLogService, never()).complete(any());
    }

    @Test
    void execute_shouldContinueWhenTerminalLogUpdateFails() {
        stubSuccess();
        when(aiCallLogService.complete(any())).thenThrow(new IllegalStateException("db down"));

        AiExecutionResult<String> result = pipeline.execute(command(TRACE_ID), raw -> "ok");

        Assertions.assertEquals("ok", result.data());
        Assertions.assertEquals(LOG_ID, result.callLogId());
        verify(aiCallLogService).complete(any());
    }

    @Test
    void executeRaw_shouldNotRequireUserForLegacyCompatibility() {
        stubSuccess();

        AiExecutionResult<String> result = pipeline.executeRaw(new AiRawExecutionCommand(
                null, REQUESTED_MODEL, "system", "user", "invalid", TRACE_ID
        ), raw -> raw);

        Assertions.assertEquals(RAW, result.data());
        Assertions.assertNull(result.callLogId());
        verify(promptTemplateResolver, never()).resolve(any());
    }

    private void stubSuccess() {
        when(aiModelClient.chat(any())).thenReturn(new AiChatResult(
                RAW, List.of(), "stop", null, null, REQUESTED_MODEL,
                REQUESTED_MODEL, 0, false, null
        ));
    }

    private AiCallLogCompletionCommand captureCompletion() {
        ArgumentCaptor<AiCallLogCompletionCommand> captor = ArgumentCaptor.forClass(AiCallLogCompletionCommand.class);
        verify(aiCallLogService).complete(captor.capture());
        return captor.getValue();
    }

    private AiExecutionCommand command(String traceId) {
        return new AiExecutionCommand(USER_ID, REQUESTED_MODEL, AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                "user prompt", "AI 今日任务排序结果格式异常", traceId);
    }

    private AiPromptTemplate template() {
        return new AiPromptTemplate(10L, "today-order.default", "today-order", 2,
                AiPromptSourceEnum.DATABASE, "system prompt");
    }

    private AiInvocationException failure(AiFailureTypeEnum type, String safeMessage) {
        return new AiInvocationException(type, REQUESTED_MODEL, 0, safeMessage,
                "model failure: " + type, null);
    }

    private static Stream<Arguments> modelFailureMappings() {
        return Stream.of(
                Arguments.of(AiFailureTypeEnum.CONFIG_ERROR, AiCallFailureTypeEnum.CONFIG),
                Arguments.of(AiFailureTypeEnum.NETWORK_ERROR, AiCallFailureTypeEnum.NETWORK),
                Arguments.of(AiFailureTypeEnum.RATE_LIMITED, AiCallFailureTypeEnum.RATE_LIMIT),
                Arguments.of(AiFailureTypeEnum.UPSTREAM_REJECTED, AiCallFailureTypeEnum.UPSTREAM_REJECTED),
                Arguments.of(AiFailureTypeEnum.UPSTREAM_SERVER_ERROR, AiCallFailureTypeEnum.UPSTREAM_SERVER),
                Arguments.of(AiFailureTypeEnum.INTERNAL_ERROR, AiCallFailureTypeEnum.INTERNAL)
        );
    }
}
