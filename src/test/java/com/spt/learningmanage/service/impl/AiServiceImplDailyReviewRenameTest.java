package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskTitleRenameLog;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.ai.TitleRenameSuggestionItemVO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplDailyReviewRenameTest {

    private static final Long USER_ID = 1L;
    private static final Long CALL_LOG_ID = 200L;
    private static final String MODEL_NAME = "qwen-test";
    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 8, 12);

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
    private TaskTitleRenameLogMapper taskTitleRenameLogMapper;
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
        AiInvocationPipeline pipeline = new AiInvocationPipeline(
                promptTemplateResolver,
                aiModelClient,
                aiCallLogService
        );
        ReflectionTestUtils.setField(aiService, "aiInvocationPipeline", pipeline);
        UserHolder.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void suggestDailyReviewRename_shouldReturnEmptyWithoutCallingAiWhenNoTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        assertEmptyResult(result);
        verify(promptTemplateResolver, never()).resolve(any());
        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
        verify(aiCallLogService, never()).createRunningLog(any());
        verify(taskTitleRenameLogMapper, never()).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldNotCallAiWhenOnlyCompletedTasksExist() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(101L, "已完成任务", TaskStatusEnum.DONE_STANDARD.getValue(), 2)
        ));

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        assertEmptyResult(result);
        verify(promptTemplateResolver, never()).resolve(any());
        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
        verify(aiCallLogService, never()).createRunningLog(any());
        verify(taskTitleRenameLogMapper, never()).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldReturnAndPersistAiSuggestions() {
        List<Task> tasks = List.of(
                task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3),
                task(102L, "写总结", TaskStatusEnum.TODO.getValue(), 2)
        );
        String response = validResponse(
                itemJson(101L, "完成核心词汇第11-12单元记忆", "标题更具体", 86),
                itemJson(102L, "完成本周学习总结初稿", "明确交付物", 82)
        );
        stubAiCall(tasks, response);

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        Assertions.assertEquals(2, result.getItems().size());
        Assertions.assertEquals(List.of(101L, 102L), result.getItems().stream()
                .map(TitleRenameSuggestionItemVO::getTaskId)
                .toList());
        Assertions.assertTrue(result.getOperationId().startsWith("20260812_rename_"));
        Assertions.assertEquals(REVIEW_DATE.toString(), result.getReviewDate());
        Assertions.assertNotNull(result.getGeneratedAt());

        ArgumentCaptor<AiCallLogCreateCommand> callLogCaptor =
                ArgumentCaptor.forClass(AiCallLogCreateCommand.class);
        verify(aiCallLogService).createRunningLog(callLogCaptor.capture());
        Assertions.assertEquals(AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT.getCode(),
                callLogCaptor.getValue().promptCode());
        Assertions.assertEquals(AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT.getScene().getCode(),
                callLogCaptor.getValue().scene());
        verify(aiCallLogService).markSuccess(eq(CALL_LOG_ID), eq(response), anyLong());
        verify(aiCallLogService, never()).markParseFailed(anyLong(), any(), anyString(), anyLong());

        ArgumentCaptor<TaskTitleRenameLog> renameLogCaptor =
                ArgumentCaptor.forClass(TaskTitleRenameLog.class);
        verify(taskTitleRenameLogMapper, times(2)).insert(renameLogCaptor.capture());
        List<TaskTitleRenameLog> logs = renameLogCaptor.getAllValues();
        Assertions.assertTrue(logs.stream().allMatch(log -> result.getOperationId().equals(log.getOperationId())));
        Assertions.assertTrue(logs.stream().allMatch(log -> USER_ID.equals(log.getUserId())));
        Assertions.assertTrue(logs.stream().allMatch(log -> Integer.valueOf(0).equals(log.getIsApplied())));
        Assertions.assertTrue(logs.stream().allMatch(log -> Integer.valueOf(0).equals(log.getIsRollback())));
        Assertions.assertEquals("背单词", logs.get(0).getOldTitle());
        Assertions.assertEquals("完成核心词汇第11-12单元记忆", logs.get(0).getNewTitle());
        Assertions.assertEquals(86, logs.get(0).getConfidence());
    }

    @Test
    void suggestDailyReviewRename_shouldFallbackAndPersistWhenInvocationFails() {
        List<Task> tasks = List.of(task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3));
        stubAiInfrastructure(tasks);
        AiInvocationException failure = invocationFailure(
                AiFailureTypeEnum.UPSTREAM_REJECTED,
                "AI 服务暂时不可用"
        );
        when(aiModelClient.invoke(eq(MODEL_NAME), anyString(), anyString())).thenThrow(failure);

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        assertFallbackSuggestion(result.getItems().get(0), 101L, "背单词");
        verify(aiCallLogService).markFailed(eq(CALL_LOG_ID), eq("AI 服务暂时不可用"), anyLong());
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
        TaskTitleRenameLog savedLog = captureSingleRenameLog();
        Assertions.assertEquals("下一步：背单词", savedLog.getNewTitle());
        Assertions.assertEquals("规则兜底：优化标题表达", savedLog.getReason());
        Assertions.assertEquals(60, savedLog.getConfidence());
    }

    @Test
    void suggestDailyReviewRename_shouldFallbackAndMarkTimeout() {
        List<Task> tasks = List.of(task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3));
        stubAiInfrastructure(tasks);
        AiInvocationException timeout = invocationFailure(
                AiFailureTypeEnum.TIMEOUT,
                "AI 服务响应超时，请稍后重试"
        );
        when(aiModelClient.invoke(eq(MODEL_NAME), anyString(), anyString())).thenThrow(timeout);

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        assertFallbackSuggestion(result.getItems().get(0), 101L, "背单词");
        verify(aiCallLogService).markTimeout(
                eq(CALL_LOG_ID),
                eq("AI 服务响应超时，请稍后重试"),
                anyLong()
        );
        verify(aiCallLogService, never()).markFailed(anyLong(), anyString(), anyLong());
        verify(taskTitleRenameLogMapper).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldFallbackWhenResponseIsNotJson() {
        String response = "not-json";
        stubAiCall(
                List.of(task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3)),
                response
        );

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        assertFallbackSuggestion(result.getItems().get(0), 101L, "背单词");
        verify(aiCallLogService).markParseFailed(
                eq(CALL_LOG_ID),
                eq(response),
                eq("AI 日报回顾改名结果格式异常"),
                anyLong()
        );
        verify(aiCallLogService, never()).markSuccess(anyLong(), anyString(), anyLong());
        Assertions.assertEquals("下一步：背单词", captureSingleRenameLog().getNewTitle());
    }

    @Test
    void suggestDailyReviewRename_shouldIgnoreAndNormalizeInvalidSuggestionItems() {
        List<Task> tasks = List.of(
                task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3),
                task(102L, "写总结", TaskStatusEnum.TODO.getValue(), 2)
        );
        String longReason = "原因".repeat(80);
        String response = validResponse(
                itemJson(999L, "未知任务", "无效ID", 80),
                itemJson(101L, "完成核心词汇复习", longReason, 120),
                itemJson(101L, "重复建议", "重复ID", 70),
                itemJson(102L, "写总结", "标题未改变", 75)
        );
        stubAiCall(tasks, response);

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request());

        Assertions.assertEquals(1, result.getItems().size());
        TitleRenameSuggestionItemVO suggestion = result.getItems().get(0);
        Assertions.assertEquals(101L, suggestion.getTaskId());
        Assertions.assertEquals(100, suggestion.getConfidence());
        Assertions.assertEquals(120, suggestion.getReason().length());
        verify(aiCallLogService).markSuccess(eq(CALL_LOG_ID), eq(response), anyLong());
        verify(aiCallLogService, never()).markParseFailed(anyLong(), any(), anyString(), anyLong());
        verify(taskTitleRenameLogMapper).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldPropagatePromptResolutionFailure() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(101L, "背单词", TaskStatusEnum.TODO.getValue(), 3)
        ));
        lenient().when(aiProperties.getBreakdownModel()).thenReturn(MODEL_NAME);
        BusinessException promptFailure = new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "提示词解析失败"
        );
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT))
                .thenThrow(promptFailure);

        BusinessException actual = Assertions.assertThrows(
                BusinessException.class,
                () -> aiService.suggestDailyReviewRename(request())
        );

        Assertions.assertSame(promptFailure, actual);
        verify(aiModelClient, never()).invoke(anyString(), anyString(), anyString());
        verify(aiCallLogService, never()).createRunningLog(any());
        verify(taskTitleRenameLogMapper, never()).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldRespectMaxEdits() {
        List<Task> tasks = List.of(
                task(101L, "任务一", TaskStatusEnum.TODO.getValue(), 3),
                task(102L, "任务二", TaskStatusEnum.TODO.getValue(), 2),
                task(103L, "任务三", TaskStatusEnum.TODO.getValue(), 1)
        );
        String response = validResponse(
                itemJson(101L, "完成任务一", "具体化", 80),
                itemJson(102L, "完成任务二", "具体化", 80),
                itemJson(103L, "完成任务三", "具体化", 80)
        );
        stubAiCall(tasks, response);
        DailyReviewSuggestRenameRequest request = request();
        request.setMaxEdits(1);

        DailyReviewSuggestRenameVO result = aiService.suggestDailyReviewRename(request);

        Assertions.assertEquals(1, result.getItems().size());
        Assertions.assertEquals(101L, result.getItems().get(0).getTaskId());
        verify(taskTitleRenameLogMapper).insert(any(TaskTitleRenameLog.class));
    }

    @Test
    void suggestDailyReviewRename_shouldRejectUnsupportedStrategy() {
        DailyReviewSuggestRenameRequest request = request();
        request.setStrategy("quick_win");

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiService.suggestDailyReviewRename(request)
        );

        Assertions.assertEquals("strategy 仅支持 balanced 或 clarity_first", exception.getMessage());
        verify(taskMapper, never()).selectList(any());
        verify(promptTemplateResolver, never()).resolve(any());
        verify(taskTitleRenameLogMapper, never()).insert(any(TaskTitleRenameLog.class));
    }

    private DailyReviewSuggestRenameRequest request() {
        DailyReviewSuggestRenameRequest request = new DailyReviewSuggestRenameRequest();
        request.setReviewDate(REVIEW_DATE.toString());
        request.setStrategy("balanced");
        request.setMaxEdits(10);
        return request;
    }

    private Task task(Long id, String title, Integer status, Integer priority) {
        Task task = new Task();
        task.setId(id);
        task.setUserId(USER_ID);
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(REVIEW_DATE);
        task.setDescription("任务描述");
        task.setCreateTime(LocalDateTime.of(2026, 8, 12, 9, 0).plusMinutes(id));
        if (TaskStatusEnum.isCompleted(status)) {
            task.setCompletedAt(LocalDateTime.of(2026, 8, 12, 18, 0));
        }
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
        when(promptTemplateResolver.resolve(AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT))
                .thenReturn(new AiPromptTemplate(
                        20L,
                        AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT.getCode(),
                        AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT.getScene().getCode(),
                        1,
                        AiPromptSourceEnum.BUILTIN,
                        "system prompt"
                ));
        when(aiCallLogService.createRunningLog(any())).thenReturn(CALL_LOG_ID);
    }

    private AiInvocationException invocationFailure(AiFailureTypeEnum failureType, String safeMessage) {
        return new AiInvocationException(
                failureType,
                MODEL_NAME,
                0,
                safeMessage,
                "upstream invocation failed",
                null
        );
    }

    private String validResponse(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private String itemJson(Long taskId,
                            String newTitle,
                            String reason,
                            Integer confidence) {
        return """
                {
                  "taskId": %d,
                  "newTitle": "%s",
                  "reason": "%s",
                  "confidence": %d
                }
                """.formatted(taskId, newTitle, reason, confidence);
    }

    private void assertEmptyResult(DailyReviewSuggestRenameVO result) {
        Assertions.assertTrue(result.getItems().isEmpty());
        Assertions.assertTrue(result.getOperationId().startsWith("20260812_rename_"));
        Assertions.assertEquals(REVIEW_DATE.toString(), result.getReviewDate());
        Assertions.assertNotNull(result.getGeneratedAt());
    }

    private void assertFallbackSuggestion(TitleRenameSuggestionItemVO suggestion,
                                          Long taskId,
                                          String oldTitle) {
        Assertions.assertEquals(taskId, suggestion.getTaskId());
        Assertions.assertEquals(oldTitle, suggestion.getOldTitle());
        Assertions.assertEquals("下一步：" + oldTitle, suggestion.getNewTitle());
        Assertions.assertEquals("规则兜底：优化标题表达", suggestion.getReason());
        Assertions.assertEquals(60, suggestion.getConfidence());
    }

    private TaskTitleRenameLog captureSingleRenameLog() {
        ArgumentCaptor<TaskTitleRenameLog> captor = ArgumentCaptor.forClass(TaskTitleRenameLog.class);
        verify(taskTitleRenameLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
