package com.spt.learningmanage.client.ai;

import com.spt.learningmanage.model.dto.ai.AiHttpResponse;

public interface AiHttpTransport {

    AiHttpResponse postChat(String url,
                            String apiKey,
                            String requestBody,
                            int connectTimeoutMs,
                            int readTimeoutMs);
}
