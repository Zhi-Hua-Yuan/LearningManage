package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private AiProperties aiProperties;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private AiModelClient aiModelClient;
    @Mock
    private PromptTemplateResolver promptTemplateResolver;

    @InjectMocks
    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
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
        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
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
        verify(aiCallLogService).updateExecutionMetadata(CALL_LOG_ID, MODEL_NAME, 0);
        verify(aiCallLogService).markSuccess(eq(CALL_LOG_ID), eq(response), anyLong());
        verify(aiCallLogService, never()).markParseFailed(anyLong(), any(), anyString(), anyLong());
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
        when(aiModelClient.invoke(eq(MODEL_NAME), anyString(), anyString())).thenThrow(failure);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).markFailed(eq(CALL_LOG_ID), eq("AI 服务暂时不可用"), anyLong());
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseIsNotJson() {
        String response = "not-json";
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(response),
                eq("AI 今日任务排序结果格式异常"),
                anyLong()
        );
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseContainsUnknownTaskId() {
        String response = validResponse(999L, 101L);
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(response),
                eq("AI 今日任务排序结果格式异常"),
                anyLong()
        );
    }

    @Test
    void recommendTodayOrder_shouldFallbackWhenResponseDuplicatesAndOmitsTaskIds() {
        String response = validResponse(101L, 101L);
        stubAiCall(fallbackTasks(), response);

        AiTodayOrderVO result = aiService.recommendTodayOrder(request());

        assertFallbackOrder(result);
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(response),
                eq("AI 今日任务排序结果格式异常"),
                anyLong()
        );
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
        task.setUserId(USER_ID);
        task.setTitle(title);
        task.setPriority(priority);
        task.setStatus(0);
        task.setDueDate(TODAY);
        task.setCreateTime(createTime);
        return task;
    }

    private void stubAiCall(List<Task> tasks, String response) {
        stubAiInfrastructure(tasks);
        when(aiModelClient.invoke(eq(MODEL_NAME), anyString(), anyString()))
                .thenReturn(new AiInvocationResult(response, MODEL_NAME, 0));
    }

    private void stubAiInfrastructure(List<Task> tasks) {
        when(taskMapper.selectList(any())).thenReturn(tasks);
        when(aiProperties.getBreakdownModel()).thenReturn(MODEL_NAME);
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
