package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMetadataOnlyLoggingTest {
    @Mock PromptTemplateResolver resolver;
    @Mock AiModelClient modelClient;
    @Mock AiCallLogService logService;

    @Test
    void metadataOnlyModeNeverPersistsPromptOrResponseBodies() {
        when(resolver.resolve(AiPromptCodeEnum.RAG_PROJECT_ANSWER)).thenReturn(
                new AiPromptTemplate(null, "rag-project-answer", "rag-project-ask", 1,
                        AiPromptSourceEnum.BUILTIN, "system secret prompt"));
        when(logService.createRunningLog(any())).thenReturn(10L);
        when(logService.complete(any())).thenReturn(true);
        when(modelClient.chat(any())).thenReturn(new AiChatResult(
                "sensitive answer", List.of(), "stop", null, "provider",
                "qwen", "qwen", 0, false, null));
        AiInvocationPipeline pipeline = new AiInvocationPipeline(resolver, modelClient, logService);

        pipeline.execute(new AiExecutionCommand(
                7L, "qwen", AiPromptCodeEnum.RAG_PROJECT_ANSWER,
                "sensitive question and evidence", "invalid", "trace1234",
                AiContentLoggingPolicy.METADATA_ONLY, "{\"evidenceCount\":1}"), raw -> raw);

        ArgumentCaptor<AiCallLogCreateCommand> create = ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        ArgumentCaptor<AiCallLogCompletionCommand> complete = ArgumentCaptor.forClass(AiCallLogCompletionCommand.class);
        verify(logService).createRunningLog(create.capture());
        verify(logService).complete(complete.capture());
        assertEquals("{\"evidenceCount\":1}", create.getValue().requestText());
        assertNull(complete.getValue().responseText());
    }
}
