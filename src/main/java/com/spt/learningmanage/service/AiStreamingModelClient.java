package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiStreamingChunk;
import reactor.core.publisher.Flux;

/**
 * Internal streaming seam for the Spring AI migration. No controller or
 * business service consumes this interface in Phase 1.
 */
public interface AiStreamingModelClient {

    Flux<AiStreamingChunk> stream(AiChatCommand command);
}
