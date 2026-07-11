package com.spt.learningmanage.client.ai;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import org.springframework.stereotype.Component;

@Component
public class HutoolAiHttpTransport implements AiHttpTransport {

    @Override
    public AiHttpResponse postChat(String url,
                                   String apiKey,
                                   String requestBody,
                                   int connectTimeoutMs,
                                   int readTimeoutMs) {
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", ContentType.JSON.getValue())
                .setConnectionTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs)
                .body(requestBody)
                .execute()) {
            return new AiHttpResponse(response.getStatus(), response.body());
        }
    }
}
