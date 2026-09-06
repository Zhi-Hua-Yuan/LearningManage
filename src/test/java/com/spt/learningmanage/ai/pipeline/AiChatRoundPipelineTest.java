package com.spt.learningmanage.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatRoundPipelineTest {
    @Test
    void toolOnlyResponseIsValidAndRoundIsLinkedToRun() {
        PromptTemplateResolver resolver = mock(PromptTemplateResolver.class);
        AiModelClient client = mock(AiModelClient.class);
        AiCallLogService logs = mock(AiCallLogService.class);
        when(resolver.resolve(AiPromptCodeEnum.AGENT_PROJECT_RISK)).thenReturn(new AiPromptTemplate(
                null, "agent.project-risk.v1", "project-risk-report", 1,
                AiPromptSourceEnum.BUILTIN, "system"));
        when(logs.createRunningLog(any())).thenReturn(9L);
        when(logs.complete(any())).thenReturn(true);
        when(client.chat(any())).thenReturn(new AiChatResult(null,
                List.of(AiToolCall.function("call-1", "queryTaskStats", "{}")),
                "tool_calls", null, "provider-1", "model", "model", 0, false, null));

        AiInvocationPipeline pipeline = new AiInvocationPipeline(resolver, client, logs);
        AiToolDefinition tool = AiToolDefinition.function(new AiFunctionDefinition(
                "queryTaskStats", "stats", new ObjectMapper().createObjectNode().put("type", "object")));
        var result = pipeline.executeChatRound(new AiChatRoundExecutionCommand(
                1L, "model", AiPromptCodeEnum.AGENT_PROJECT_RISK,
                List.of(AiChatMessage.user("analyze")), List.of(tool), AiToolChoice.auto(),
                0.0, 1000, "trace-1", "safe-summary", "run-1", 2));

        assertNull(result.data().content());
        assertEquals("queryTaskStats", result.data().toolCalls().get(0).function().name());
        ArgumentCaptor<AiCallLogCreateCommand> captor = ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        verify(logs).createRunningLog(captor.capture());
        assertEquals("run-1", captor.getValue().agentRunId());
        assertEquals(2, captor.getValue().agentRoundNo());
    }
}
