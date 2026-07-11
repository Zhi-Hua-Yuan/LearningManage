package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.ai.AiInvocationResult;

public interface AiModelClient {

    AiInvocationResult invoke(String primaryModel, String systemPrompt, String userPrompt);
}
