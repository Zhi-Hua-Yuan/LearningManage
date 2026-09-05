package com.spt.learningmanage.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeEmbeddingServiceImplTest {

    @Test
    void logsHashesAndUsageButNeverSourceTextOrVectors() {
        EmbeddingClient client = mock(EmbeddingClient.class);
        AiCallLogService logs = mock(AiCallLogService.class);
        when(logs.createRunningLog(any(AiCallLogCreateCommand.class))).thenReturn(7L);
        when(logs.complete(any(AiCallLogCompletionCommand.class))).thenReturn(true);
        when(client.embedDocuments(any(), any())).thenReturn(new EmbeddingBatchResult(
                List.of(List.of(0.1f, 0.2f)), "text-embedding-v4", 5L, 5L, "provider-1"));
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(2);
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(
                client, logs, properties, new ObjectMapper());

        service.embedDocuments(List.of("private source body"),
                new EmbeddingCallContext(1L, "trace-id", List.of("hash-only")));

        ArgumentCaptor<AiCallLogCreateCommand> create = ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        verify(logs).createRunningLog(create.capture());
        assertFalse(create.getValue().requestText().contains("private source body"));
        assertEquals("knowledge-index", create.getValue().scene());
        ArgumentCaptor<AiCallLogCompletionCommand> completion =
                ArgumentCaptor.forClass(AiCallLogCompletionCommand.class);
        verify(logs).complete(completion.capture());
        assertEquals(AiCallLogStatusEnum.SUCCESS, completion.getValue().status());
        assertEquals(5, completion.getValue().usage().promptTokens());
        assertFalse(completion.getValue().responseText().contains("0.1"));
    }
}
