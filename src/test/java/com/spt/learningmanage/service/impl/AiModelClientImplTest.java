package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.SocketTimeoutException;

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

    private AiHttpResponse successResponse(String content) {
        return new AiHttpResponse(
                200,
                "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}"
        );
    }
}
