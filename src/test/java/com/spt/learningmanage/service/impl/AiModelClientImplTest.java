package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelClientImplTest {

    private AiProperties aiProperties;

    private AiHttpTransport aiHttpTransport;

    private AiModelClientImpl aiModelClient;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setBaseUrl("https://example.test/v1");
        aiProperties.setApiKey("test-key");
        aiProperties.setModel("primary-model");
        aiProperties.setFallbackModel("fallback-model");
        aiProperties.setConnectTimeoutMs(5000);
        aiProperties.setReadTimeoutMs(60000);

        aiHttpTransport = Mockito.mock(AiHttpTransport.class);
        aiModelClient = new AiModelClientImpl(aiProperties, aiHttpTransport);
    }

    @Test
    void invoke_shouldReturnPrimaryModelResult() {
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(successResponse("主模型结果"));

        AiInvocationResult result = aiModelClient.invoke("primary-model", "system", "user");

        Assertions.assertEquals("主模型结果", result.content());
        Assertions.assertEquals("primary-model", result.actualModel());
        Assertions.assertEquals(0, result.retryCount());
        verify(aiHttpTransport).postChat(anyString(), anyString(), anyString(),
                Mockito.eq(5000), Mockito.eq(60000));
    }

    @Test
    void invoke_shouldUseFallbackModelAfterPrimaryTimeout() {
        RuntimeException timeout = new RuntimeException(new SocketTimeoutException("read timed out"));
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(timeout)
                .thenReturn(successResponse("兜底模型结果"));

        AiInvocationResult result = aiModelClient.invoke("primary-model", "system", "user");

        Assertions.assertEquals("兜底模型结果", result.content());
        Assertions.assertEquals("fallback-model", result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
        verify(aiHttpTransport, times(2))
                .postChat(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void invoke_shouldNotRetryRejectedRequest() {
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(401, "sensitive upstream response"));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class,
                () -> aiModelClient.invoke("primary-model", "system", "user")
        );

        Assertions.assertEquals(AiFailureTypeEnum.UPSTREAM_REJECTED, exception.getFailureType());
        Assertions.assertEquals(0, exception.getRetryCount());
        Assertions.assertFalse(exception.getSafeMessage().contains("sensitive upstream response"));
        Assertions.assertFalse(exception.getMessage().contains("sensitive upstream response"));
        verify(aiHttpTransport).postChat(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void invoke_shouldExposeFinalTimeoutMetadataWhenBothModelsTimeout() {
        RuntimeException timeout = new RuntimeException(new SocketTimeoutException("read timed out"));
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(timeout)
                .thenThrow(timeout);

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class,
                () -> aiModelClient.invoke("primary-model", "system", "user")
        );

        Assertions.assertEquals(AiFailureTypeEnum.TIMEOUT, exception.getFailureType());
        Assertions.assertEquals("fallback-model", exception.getModelName());
        Assertions.assertEquals(1, exception.getRetryCount());
        Assertions.assertEquals(1, exception.getSuppressed().length);
    }

    @Test
    void invoke_shouldClassifyInvalidProviderResponse() {
        aiProperties.setFallbackModel("primary-model");
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(200, "{\"choices\":[]}"));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class,
                () -> aiModelClient.invoke("primary-model", "system", "user")
        );

        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, exception.getFailureType());
        Assertions.assertEquals("AI 返回结果格式异常，请重试", exception.getSafeMessage());
    }

    @Test
    void invoke_shouldClassifyGatewayTimeoutAsTimeout() {
        aiProperties.setFallbackModel("primary-model");
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(504, "gateway timeout"));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class,
                () -> aiModelClient.invoke("primary-model", "system", "user")
        );

        Assertions.assertEquals(AiFailureTypeEnum.TIMEOUT, exception.getFailureType());
        Assertions.assertEquals("AI 服务响应超时，请稍后重试", exception.getSafeMessage());
    }

    @Test
    void chat_shouldParseToolCallsUsageAndProviderRequestId() {
        aiProperties.setFallbackModel("primary-model");
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(200, """
                        {"id":"provider-1","choices":[{"message":{"role":"assistant","content":null,
                        "tool_calls":[{"id":"call-1","type":"function","function":{
                        "name":"query_tasks","arguments":"{\\"projectId\\":1001}"}}]},
                        "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":8,"completion_tokens":3,"total_tokens":11}}
                        """));

        AiChatResult result = aiModelClient.chat(toolCommand("primary-model"));

        Assertions.assertNull(result.content());
        Assertions.assertEquals(1, result.toolCalls().size());
        Assertions.assertEquals("query_tasks", result.toolCalls().get(0).function().name());
        Assertions.assertEquals("tool_calls", result.finishReason());
        Assertions.assertEquals(11, result.usage().totalTokens());
        Assertions.assertEquals("provider-1", result.providerRequestId());
        Assertions.assertFalse(result.fallbackUsed());
    }

    @Test
    void chat_shouldPreserveCommandAndExposeFallbackMetadataAfterRateLimit() throws Exception {
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(429, "rate limited"))
                .thenReturn(new AiHttpResponse(
                        200,
                        "{\"choices\":[{\"message\":{\"content\":\"fallback\"},\"finish_reason\":\"stop\"}]}",
                        Map.of("X-Request-ID", List.of("fallback-request"))
                ));

        AiChatResult result = aiModelClient.chat(toolCommand("primary-model"));

        Assertions.assertEquals("fallback", result.content());
        Assertions.assertEquals("primary-model", result.requestedModel());
        Assertions.assertEquals("fallback-model", result.actualModel());
        Assertions.assertEquals(1, result.retryCount());
        Assertions.assertTrue(result.fallbackUsed());
        Assertions.assertEquals(AiFailureTypeEnum.RATE_LIMITED, result.fallbackReason());
        Assertions.assertEquals("fallback-request", result.providerRequestId());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiHttpTransport, times(2)).postChat(
                anyString(), anyString(), bodyCaptor.capture(), anyInt(), anyInt());
        ObjectMapper mapper = new ObjectMapper();
        Assertions.assertEquals("primary-model", mapper.readTree(bodyCaptor.getAllValues().get(0)).get("model").asText());
        Assertions.assertEquals("fallback-model", mapper.readTree(bodyCaptor.getAllValues().get(1)).get("model").asText());
        Assertions.assertEquals(
                mapper.readTree(bodyCaptor.getAllValues().get(0)).get("messages"),
                mapper.readTree(bodyCaptor.getAllValues().get(1)).get("messages")
        );
        Assertions.assertEquals(
                mapper.readTree(bodyCaptor.getAllValues().get(0)).get("tools"),
                mapper.readTree(bodyCaptor.getAllValues().get(1)).get("tools")
        );
    }

    @Test
    void chat_shouldFallbackAfterServerErrorButNotAfterRejectedRequest() {
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(500, "upstream error"))
                .thenReturn(successResponse("fallback"));
        Assertions.assertEquals("fallback", aiModelClient.chat(textCommand()).content());

        Mockito.reset(aiHttpTransport);
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(401, "rejected"));
        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class, () -> aiModelClient.chat(textCommand()));
        Assertions.assertEquals(AiFailureTypeEnum.UPSTREAM_REJECTED, exception.getFailureType());
        verify(aiHttpTransport, times(1)).postChat(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void chat_shouldValidateCommandBeforeTransport() {
        AiChatCommand invalid = new AiChatCommand(
                "primary-model", List.of(), List.of(), null, null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> aiModelClient.chat(invalid));
        verify(aiHttpTransport, times(0)).postChat(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void chat_shouldRejectToolCallThatWasNotDeclaredByCommand() {
        aiProperties.setFallbackModel("primary-model");
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(200, """
                        {"choices":[{"message":{"content":null,"tool_calls":[{
                        "id":"call-1","type":"function","function":{"name":"query_stats","arguments":"{}"}
                        }]},"finish_reason":"tool_calls"}]}
                        """));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class, () -> aiModelClient.chat(toolCommand("primary-model")));

        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, exception.getFailureType());
    }

    @Test
    void invoke_shouldRejectToolOnlyResponse() {
        aiProperties.setFallbackModel("primary-model");
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(200, """
                        {"choices":[{"message":{"content":null,"tool_calls":[{
                        "id":"call-1","type":"function","function":{"name":"query_tasks","arguments":"{}"}
                        }]},"finish_reason":"tool_calls"}]}
                        """));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class,
                () -> aiModelClient.invoke("primary-model", "system", "user")
        );

        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, exception.getFailureType());
    }

    @Test
    void chat_shouldClassifyMalformedResponseWithoutLeakingBody() {
        aiProperties.setFallbackModel("primary-model");
        String sensitiveBody = "not-json-api-key-secret";
        when(aiHttpTransport.postChat(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new AiHttpResponse(200, sensitiveBody));

        AiInvocationException exception = Assertions.assertThrows(
                AiInvocationException.class, () -> aiModelClient.chat(textCommand()));

        Assertions.assertEquals(AiFailureTypeEnum.INVALID_RESPONSE, exception.getFailureType());
        Assertions.assertFalse(exception.getSafeMessage().contains(sensitiveBody));
        Assertions.assertFalse(exception.getMessage().contains(sensitiveBody));
    }

    private AiChatCommand textCommand() {
        return new AiChatCommand(
                "primary-model",
                List.of(AiChatMessage.system("system"), AiChatMessage.user("user")),
                List.of(),
                AiToolChoice.none(),
                null,
                null
        );
    }

    private AiChatCommand toolCommand(String model) {
        ObjectMapper objectMapper = new ObjectMapper();
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "query_tasks",
                "查询任务",
                objectMapper.createObjectNode().put("type", "object")
        ));
        return new AiChatCommand(
                model,
                List.of(AiChatMessage.user("查询项目任务")),
                List.of(tool),
                AiToolChoice.auto(),
                0.2D,
                2000
        );
    }

    private AiHttpResponse successResponse(String content) {
        return new AiHttpResponse(
                200,
                "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}"
        );
    }
}
