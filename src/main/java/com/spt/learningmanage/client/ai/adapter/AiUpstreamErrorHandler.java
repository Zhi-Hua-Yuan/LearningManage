package com.spt.learningmanage.client.ai.adapter;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Spring AI 路径上的上游错误处理器：把非 2xx 转成携带状态码的
 * {@link AiUpstreamHttpException}，供适配器按与 legacy 相同的规则分类。
 *
 * <p>它替换掉 Spring AI 默认的 {@code RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER}
 * （那个实现会抛 {@code TransientAiException} / {@code NonTransientAiException}，
 * 状态码只剩在消息文本里）。替换是安全的：框架级重试已被压到
 * {@code maxAttempts=1}，本来就不会依赖这些异常类型。</p>
 */
public class AiUpstreamErrorHandler implements ResponseErrorHandler {

    /**
     * 响应正文的读取上限。上游出错时可能回很长的正文，
     * 这里只留足够诊断的前缀，避免把整段错误载荷带进内存与日志。
     */
    private static final int MAX_BODY_CHARS = 512;

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        handleError(response);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        throw new AiUpstreamHttpException(statusCode, readBody(response));
    }

    private String readBody(ClientHttpResponse response) {
        try {
            byte[] body = response.getBody().readNBytes(MAX_BODY_CHARS);
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
