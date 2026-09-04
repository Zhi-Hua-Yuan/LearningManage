package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;

public interface AiModelClient {

    AiChatResult chat(AiChatCommand command);

    AiInvocationResult invoke(String primaryModel, String systemPrompt, String userPrompt);
}
