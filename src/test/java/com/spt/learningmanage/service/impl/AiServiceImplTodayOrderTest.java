package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.scene.TodayOrderAiServiceImpl;
import com.spt.learningmanage.service.impl.ai.support.AiJsonResponseSanitizerImpl;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTodayOrderTest {

    private static final Long USER_ID = 1L;
    private static final Long CALL_LOG_ID = 100L;
    private static final String MODEL_NAME = "qwen-test";
    private static final String NOW = "2026-08-12T10:30:00";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Task.class
        );
    }

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private AiModelClient aiModelClient;
    @Mock
    private PromptTemplateResolver promptTemplateResolver;
    @Mock
    private PermissionService permissionService;

    @Mock
    private AiModelSelector modelSelector;
    private TodayOrderAiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        AiInvocationPipeline pipeline = new AiInvocationPipeline(
                promptTemplateResolver,
                aiModelClient,
                aiCallLogService
        );
        aiService = new TodayOrderAiServiceImpl(
                taskMapper, pipeline, permissionService, modelSelector,
                new AiJsonResponseSanitizerImpl());
        lenient().when(permissionService.filterReadableTaskIds(eq(USER_ID), any()))
                .thenAnswer(invocation -> new java.util.LinkedHashSet<>(invocation.getArgument(1)));
        UserHolder.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void recommendTodayOrder_shouldReturnEmptyResultWithoutCallingAiWhenNoTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        Assertions.assertEquals("balanced", result.getStrategy());
        Assertions.assertFalse(result.getFallbackUsed());
        Assertions.assertTrue(result.getItems().isEmpty());
        Assertions.assertNotNull(result.getGeneratedAt());
        verify(promptTemplateResolver, never()).resolve(any());
        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    @Test
    void recommendTodayOrder_shouldReturnAiOrderWhenResponseIsValid() {
        List<Task> tasks = List.of(
                task(101L, "普通任务", 1, LocalDateTime.of(2026, 8, 12, 9, 0)),
                task(102L, "高优任务", 3, LocalDateTime.of(2026, 8, 12, 9, 30))
        );
        String response = validResponse(102L, 101L);
        stubAiCall(tasks, response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        Assertions.assertFalse(result.getFallbackUsed());
        Assertions.assertEquals("balanced", result.getStrategy());
        Assertions.assertEquals(List.of(102L, 101L), result.getItems().stream().map(item -> item.getTaskId()).toList());
        Assertions.assertEquals(List.of(1, 2), result.getItems().stream().map(item -> item.getRank()).toList());
        Assertions.assertNotNull(result.getGeneratedAt());
        verify(aiCallLogService).complete(argThat(command -> !command.degraded()
                && command.status() == com.spt.learningmanage.constant.AiCallLogStatusEnum.SUCCESS));
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenModelInvocationFails() {
        List<Task> tasks = fallbackTasks();
        stubAiInfrastructure(tasks);
        AiInvocationException failure = new AiInvocationException(
                AiFailureTypeEnum.UPSTREAM_REJECTED,
                MODEL_NAME,
                0,
                "AI 服务暂时不可用",
                "upstream rejected request",
                null
        );
        when(aiModelClient.chat(any())).thenThrow(failure);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).complete(argThat(command -> command.degraded()
                && command.failureType() == com.spt.learningmanage.constant.AiCallFailureTypeEnum.UPSTREAM_REJECTED));
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseIsNotJson() {
        String response = "not-json";
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).complete(argThat(command -> command.degraded()
                && command.failureType() == com.spt.learningmanage.constant.AiCallFailureTypeEnum.RESPONSE_PARSE));
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseContainsUnknownTaskId() {
        String response = validResponse(999L, 101L);
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).complete(argThat(command -> command.degraded()));
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseDuplicatesAndOmitsTaskIds() {
        String response = validResponse(101L, 101L);
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).complete(argThat(command -> command.degraded()));
    }

    @Test
    void recommendTodayOrder_shouldUseAssigneeForAutomaticCandidates() {
        stubAiCall(fallbackTasks(), validResponse(102L, 101L));

        aiService.recommendTodayOrder(request());

        ArgumentCaptor<Wrapper<Task>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("assignee_user_id"));
    }

    @Test
    void recommendTodayOrder_shouldRejectPartialExplicitSelectionBeforeAi() {
        AiTodayOrderRequest request = request();
        request.setTaskIds(List.of(101L, 102L));
        when(permissionService.requireAllTasksReadable(USER_ID, request.getTaskIds()))
                .thenReturn(new java.util.LinkedHashSet<>(request.getTaskIds()));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(101L, "普通任务", 1,
                LocalDateTime.of(2026, 8, 12, 9, 0))));

        assertThrows(BusinessException.class, () -> aiService.recommendTodayOrder(request));
        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    @Test
    void recommendTodayOrder_shouldRejectMissingExplicitTaskBeforeAi() {
        AiTodayOrderRequest request = request();
        request.setTaskIds(List.of(999L));
        when(permissionService.requireAllTasksReadable(USER_ID, request.getTaskIds()))
                .thenReturn(new java.util.LinkedHashSet<>(request.getTaskIds()));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> aiService.recommendTodayOrder(request));
        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    @Test
    void recommendTodayOrder_shouldRejectUnauthorizedExplicitTaskBeforeAi() {
        AiTodayOrderRequest request = request();
        request.setTaskIds(List.of(998L));
        doThrow(new BusinessException(com.spt.learningmanage.exception.ErrorCode.NO_AUTH_ERROR, "无权访问任务"))
                .when(permissionService).requireAllTasksReadable(USER_ID, request.getTaskIds());

        assertThrows(BusinessException.class, () -> aiService.recommendTodayOrder(request));
        verify(taskMapper, never()).selectList(any());
        verify(aiModelClient, never()).chat(any());
        verify(aiCallLogService, never()).createRunningLog(any());
    }

    private AiTodayOrderRequest request() {
        AiTodayOrderRequest request = new AiTodayOrderRequest();
        request.setNow(NOW);
        request.setTimezone("Asia/Shanghai");
        request.setStrategy("balanced");
        request.setLimit(20);
        return request;
    }

    private List<Task> fallbackTasks() {
        return List.of(
                task(101L, "普通任务", 1, LocalDateTime.of(2026, 8, 12, 9, 0)),
                task(102L, "高优任务", 3, LocalDateTime.of(2026, 8, 12, 9, 30))
        );
    }

    private Task task(Long id, String title, Integer priority, LocalDateTime createTime) {
        Task task = new Task();
        task.setId(id);
        task.setCreatedByUserId(USER_ID);
        task.setTitle(title);
        task.setPriority(priority);
        task.setStatus(0);
        task.setDueDate(TODAY);
        task.setCreateTime(createTime);
        return task;
    }

    private void stubAiCall(List<Task> tasks, String response) {
        stubAiInfrastructure(tasks);
        when(aiModelClient.chat(any())).thenReturn(new AiChatResult(
                response, List.of(), "stop", null, null,
                MODEL_NAME, MODEL_NAME, 0, false, null
        ));
    }

    private void stubAiInfrastructure(List<Task> tasks) {
        when(taskMapper.selectList(any())).thenReturn(tasks);
        when(modelSelector.breakdownModel()).thenReturn(MODEL_NAME);
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.TODAY_ORDER_DEFAULT))
                .thenReturn(new AiPromptTemplate(
                        10L,
                        AiPromptCodeEnum.TODAY_ORDER_DEFAULT.getCode(),
                        AiPromptCodeEnum.TODAY_ORDER_DEFAULT.getScene().getCode(),
                        1,
                        AiPromptSourceEnum.BUILTIN,
                        "system prompt"
                ));
        when(aiCallLogService.createRunningLog(any())).thenReturn(CALL_LOG_ID);
        when(aiCallLogService.complete(any())).thenReturn(true);
    }

    private String validResponse(Long firstTaskId, Long secondTaskId) {
        return """
                {
                  "strategy": "balanced",
                  "items": [
                    {
                      "taskId": %d,
                      "difficulty": 3,
                      "cost": 2,
                      "benefit": 5,
                      "estimatedMinutes": 30,
                      "reason": "优先处理"
                    },
                    {
                      "taskId": %d,
                      "difficulty": 2,
                      "cost": 2,
                      "benefit": 3,
                      "estimatedMinutes": 20,
                      "reason": "随后处理"
                    }
                  ]
                }
                """.formatted(firstTaskId, secondTaskId);
    }

    private void assertFallbackOrder(AiTodayOrderVO result) {
        Assertions.assertTrue(result.getFallbackUsed());
        Assertions.assertEquals("balanced", result.getStrategy());
        Assertions.assertEquals(List.of(102L, 101L), result.getItems().stream().map(item -> item.getTaskId()).toList());
        Assertions.assertTrue(result.getItems().stream()
                .allMatch(item -> item.getReason().startsWith("规则兜底：")));
    }
}
