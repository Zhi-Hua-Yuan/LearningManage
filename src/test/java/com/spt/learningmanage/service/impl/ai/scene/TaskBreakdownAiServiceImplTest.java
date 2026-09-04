package com.spt.learningmanage.service.impl.ai.scene;

import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiJsonResponseSanitizerImpl;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskBreakdownAiServiceImplTest {

    private final AiDraftLifecycleService draftLifecycleService = mock(AiDraftLifecycleService.class);
    private final AiDraftConfirmationService draftConfirmationService = mock(AiDraftConfirmationService.class);
    private final AiModelSelector modelSelector = mock(AiModelSelector.class);
    private final PromptTemplateResolver promptTemplateResolver = mock(PromptTemplateResolver.class);
    private final AiModelClient modelClient = mock(AiModelClient.class);
    private final AiCallLogService callLogService = mock(AiCallLogService.class);

    private TaskBreakdownAiServiceImpl service;

    @BeforeEach
    void setUp() {
        AiInvocationPipeline pipeline = new AiInvocationPipeline(promptTemplateResolver, modelClient, callLogService);
        service = new TaskBreakdownAiServiceImpl(
                pipeline, draftLifecycleService, draftConfirmationService,
                modelSelector, new AiJsonResponseSanitizerImpl());
        UserHolder.set(1L);
        when(modelSelector.breakdownModel()).thenReturn("qwen-test");
        when(promptTemplateResolver.resolve(any())).thenAnswer(invocation -> {
            AiPromptCodeEnum code = invocation.getArgument(0);
            return new AiPromptTemplate(1L, code.getCode(), code.getScene().getCode(),
                    1, AiPromptSourceEnum.BUILTIN, "system prompt");
        });
        when(callLogService.createRunningLog(any())).thenReturn(1L);
        when(callLogService.complete(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        MDC.clear();
    }

    @Test
    void generateTaskBreakdownParsesAndNormalizesValidResponse() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2)));

        List<MilestoneDraftVO> result = service.generateTaskBreakdown("通过考试", "", "8周", false);

        assertEquals(1, result.size());
        assertEquals("准备阶段", result.get(0).getName());
        assertEquals(2, result.get(0).getTasks().get(0).getPriority());
        assertEquals("2026-09-10", result.get(0).getTasks().get(0).getDueDate());
    }

    @Test
    void generateTaskBreakdownMapsBusinessValidationFailureToStableError() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(9)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateTaskBreakdown("通过考试", "", "8周", false));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void previewCreatesExistingDraftPayloadContract() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2)));
        AiDraft draft = new AiDraft();
        draft.setDraftId("draft-1");
        draft.setExpireAt(LocalDateTime.now().plusMinutes(20));
        when(draftLifecycleService.buildInputHash(any())).thenReturn("hash");
        when(draftLifecycleService.createDraft(any()))
                .thenReturn(draft);
        AiBreakdownRequest request = new AiBreakdownRequest();
        request.setTarget("通过考试");
        request.setDuration("8周");

        AiBreakdownPreviewVO result = service.previewTaskBreakdown(request);

        assertEquals("draft-1", result.getDraftId());
        assertNotNull(result.getExpireAt());
        assertEquals(1, result.getMilestones().size());
        verify(draftLifecycleService).createDraft(any());
    }

    @Test
    void previewPropagatesHttpTraceToPipelineAndDraft() {
        MDC.put("traceId", "http_trace-12345");
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2)));
        AiDraft draft = new AiDraft();
        draft.setDraftId("draft-trace");
        draft.setExpireAt(LocalDateTime.now().plusMinutes(20));
        when(draftLifecycleService.buildInputHash(any())).thenReturn("hash");
        when(draftLifecycleService.createDraft(any())).thenReturn(draft);
        AiBreakdownRequest request = new AiBreakdownRequest();
        request.setTarget("通过考试");
        request.setDuration("8周");

        service.previewTaskBreakdown(request);

        ArgumentCaptor<AiDraftCreateCommand> captor = ArgumentCaptor.forClass(AiDraftCreateCommand.class);
        verify(draftLifecycleService).createDraft(captor.capture());
        assertEquals("http_trace-12345", captor.getValue().traceId());
    }

    @Test
    void confirmCreatesProjectMilestoneAndTasksAndMarksDraft() {
        AiDraftConfirmVO confirmation = new AiDraftConfirmVO();
        confirmation.setSuccess(true);
        confirmation.setBusinessId(101L);
        when(draftConfirmationService.confirm(any())).thenReturn(confirmation);

        AiDraftConfirmVO result = service.confirmTaskBreakdown("draft-1", "op-1", null, null);

        assertTrue(result.getSuccess());
        assertEquals(101L, result.getBusinessId());
        verify(draftConfirmationService).confirm(any());
    }

    private AiChatResult chatResult(String content) {
        return new AiChatResult(content, List.of(), "stop", null, null,
                "qwen-test", "qwen-test", 0, false, null);
    }

    private String validResponse(int priority) {
        return """
                ```json
                [{
                  "name": " 准备阶段 ",
                  "tasks": [{
                    "name": " 制定计划 ",
                    "priority": %d,
                    "dueDate": "2026-09-10"
                  }]
                }]
                ```
                """.formatted(priority);
    }
}
