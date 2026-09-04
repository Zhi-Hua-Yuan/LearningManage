package com.spt.learningmanage.service.impl.ai.scene;

import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatCompatibilityServiceImplTest {

    private final AiModelClient modelClient = mock(AiModelClient.class);
    private final AiCallLogService callLogService = mock(AiCallLogService.class);
    private final AiModelSelector modelSelector = mock(AiModelSelector.class);
    private final AiChatCompatibilityServiceImpl service = new AiChatCompatibilityServiceImpl(
            new AiInvocationPipeline(mock(PromptTemplateResolver.class), modelClient, callLogService),
            modelSelector);

    @AfterEach
    void clearUser() {
        com.spt.learningmanage.utils.UserHolder.remove();
    }

    @Test
    void blankPromptIsRejectedBeforeModelCall() {
        assertThrows(BusinessException.class, () -> service.chat(" ", "user"));
        verify(modelClient, never()).chat(any());
    }

    @Test
    void validPromptUsesSelectedDefaultModel() {
        when(modelSelector.defaultModel()).thenReturn("qwen-test");
        when(callLogService.createRunningLog(any())).thenReturn(null);
        when(modelClient.chat(any())).thenReturn(new AiChatResult(
                "answer", List.of(), "stop", null, null,
                "qwen-test", "qwen-test", 0, false, null));

        assertEquals("answer", service.chat("system", "user"));
        verify(modelClient).chat(any());
    }
}
