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
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
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
import java.time.LocalDate;
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
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2, false)));

        List<MilestoneDraftVO> result = service.generateTaskBreakdown("通过考试", "", "8周", false);

        assertEquals(3, result.size());
        assertEquals("阶段1", result.get(0).getName());
        assertEquals(3, result.get(0).getTasks().size());
        assertEquals(2, result.get(0).getTasks().get(0).getPriority());
        assertEquals(LocalDate.now().plusDays(5).toString(), result.get(0).getTasks().get(0).getDueDate());
    }

    @Test
    void generateTaskBreakdownEnforcesModeSpecificResponseShape() {
        when(modelClient.chat(any())).thenReturn(
                chatResult(responseWithShape(2, 3, 3)),
                chatResult(responseWithShape(2, 3, 4)),
                chatResult(responseWithShape(2, 2, 3)),
                chatResult(responseWithShape(2, 3, 4)));

        assertEquals(3, service.generateTaskBreakdown("默认拆解", "", "8周", false).get(0).getTasks().size());
        assertEquals(4, service.generateTaskBreakdown("详细拆解", "", "8周", true).get(0).getTasks().size());
        assertEquals(ErrorCode.AI_RESPONSE_INVALID, assertThrows(BusinessException.class,
                () -> service.generateTaskBreakdown("里程碑数量错误", "", "8周", false)).getErrorCode());
        assertEquals(ErrorCode.AI_RESPONSE_INVALID, assertThrows(BusinessException.class,
                () -> service.generateTaskBreakdown("默认任务数量错误", "", "8周", false)).getErrorCode());
    }

    @Test
    void generateTaskBreakdownSuppliesAnExplicitPlanningWindow() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2, false)));

        service.generateTaskBreakdown("通过考试", "", "8周", false);

        ArgumentCaptor<AiChatCommand> captor = ArgumentCaptor.forClass(AiChatCommand.class);
        verify(modelClient).chat(captor.capture());
        String userPrompt = captor.getValue().messages().get(1).content();
        LocalDate today = LocalDate.now();
        assertTrue(userPrompt.contains("今天日期（含）：" + today));
        assertTrue(userPrompt.contains("最晚截止日期（含）：" + today.plusWeeks(8)));
        assertTrue(userPrompt.contains("绝不能晚于最晚截止日期"));
    }

    @Test
    void generateTaskBreakdownMapsBusinessValidationFailureToStableError() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(9, false)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateTaskBreakdown("通过考试", "", "8周", false));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void generateTaskBreakdownRejectsDueDateOutsidePlanningWindow() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2, false)
                .replace(LocalDate.now().plusDays(5).toString(), LocalDate.now().plusWeeks(8).plusDays(1).toString())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.generateTaskBreakdown("通过考试", "", "8周", false));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void previewCreatesExistingDraftPayloadContract() {
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2, false)));
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
        assertEquals(3, result.getMilestones().size());
        verify(draftLifecycleService).createDraft(any());
    }

    @Test
    void previewPropagatesHttpTraceToPipelineAndDraft() {
        MDC.put("traceId", "http_trace-12345");
        when(modelClient.chat(any())).thenReturn(chatResult(validResponse(2, false)));
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

    private String validResponse(int priority, boolean detailed) {
        return responseWithShape(priority, 3, detailed ? 4 : 3);
    }

    private String responseWithShape(int priority, int milestoneCount, int tasksPerMilestone) {
        StringBuilder response = new StringBuilder("```json\n[");
        for (int milestone = 1; milestone <= milestoneCount; milestone++) {
            if (milestone > 1) {
                response.append(',');
            }
            response.append("{\"name\":\" 阶段").append(milestone).append(" \",\"tasks\":[");
            for (int task = 1; task <= tasksPerMilestone; task++) {
                if (task > 1) {
                    response.append(',');
                }
                response.append("{\"name\":\" 任务").append(milestone).append('-').append(task)
                        .append(" \",\"priority\":").append(priority)
                        .append(",\"dueDate\":\"").append(LocalDate.now().plusDays(5)).append("\"}");
            }
            response.append("]}");
        }
        return response.append("]\n```").toString();
    }
}
