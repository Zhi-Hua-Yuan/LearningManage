package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskTitleRenameLog;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.ai.TitleRenameSuggestionItemVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.scene.DailyRenameAiService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyRenameAiServiceImpl extends AiSceneSupport implements DailyRenameAiService {

    private static final Logger log = LoggerFactory.getLogger(DailyRenameAiServiceImpl.class);
    private static final int TASK_TITLE_MAX_LEN = 60;
    private static final int DAILY_RENAME_MAX_EDITS_DEFAULT = 10;
    private static final int DAILY_RENAME_MAX_EDITS_MAX = 50;
    private static final int DAILY_RENAME_REASON_MAX_LEN = 120;

    private final TaskMapper taskMapper;
    private final TaskTitleRenameLogMapper taskTitleRenameLogMapper;
    private final AiInvocationPipeline aiInvocationPipeline;
    private final PermissionService permissionService;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public DailyRenameAiServiceImpl(TaskMapper taskMapper,
                                    TaskTitleRenameLogMapper taskTitleRenameLogMapper,
                                    AiInvocationPipeline aiInvocationPipeline,
                                    PermissionService permissionService,
                                    AiModelSelector modelSelector,
                                    AiJsonResponseSanitizer jsonSanitizer) {
        this.taskMapper = taskMapper;
        this.taskTitleRenameLogMapper = taskTitleRenameLogMapper;
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.permissionService = permissionService;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Override
    public DailyReviewSuggestRenameVO suggestDailyReviewRename(DailyReviewSuggestRenameRequest request) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        DailyReviewSuggestRenameRequest safeRequest = request == null ? new DailyReviewSuggestRenameRequest() : request;
        LocalDate reviewDate = resolveReviewDate(safeRequest.getReviewDate());
        String strategy = normalizeRenameStrategy(safeRequest.getStrategy());
        int maxEdits = resolveRenameMaxEdits(safeRequest.getMaxEdits());
        List<Task> dayTasks = loadDailyReviewTasks(currentUserId, safeRequest.getTaskIds(), reviewDate);
        List<Task> completedTasks = dayTasks.stream()
                .filter(task -> TaskStatusEnum.isCompleted(task.getStatus())).toList();
        List<Task> pendingTasks = dayTasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), TaskStatusEnum.TODO.getValue())).toList();

        String operationId = generateRenameOperationId(reviewDate);
        DailyReviewSuggestRenameVO result = new DailyReviewSuggestRenameVO();
        result.setOperationId(operationId);
        result.setGeneratedAt(LocalDateTime.now().toString());
        result.setReviewDate(reviewDate.toString());
        if (pendingTasks.isEmpty()) {
            result.setItems(List.of());
            return result;
        }

        String userPrompt = buildDailyReviewRenameUserPrompt(
                reviewDate, strategy, maxEdits, completedTasks, pendingTasks);
        AiExecutionCommand command = new AiExecutionCommand(
                currentUserId,
                modelSelector.breakdownModel(),
                AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT,
                userPrompt,
                "AI 日报回顾改名结果格式异常"
        );
        List<TitleRenameSuggestionItemVO> suggestions = aiInvocationPipeline.execute(
                command,
                rawContent -> parseAndValidateRenameSuggestions(rawContent, pendingTasks, maxEdits),
                failure -> {
                    log.warn("AI 日报回顾改名失败，回退规则生成。userId={}, reviewDate={}, type={}",
                            currentUserId, reviewDate, failure.failureType(), failure.cause());
                    return fallbackRenameSuggestions(pendingTasks, maxEdits);
                }
        ).data();
        saveRenameLogs(currentUserId, reviewDate, operationId, suggestions);
        result.setItems(suggestions);
        return result;
    }

    private LocalDate resolveReviewDate(String reviewDateText) {
        if (StrUtil.isBlank(reviewDateText)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(reviewDateText.trim());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reviewDate 格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeRenameStrategy(String strategy) {
        if (StrUtil.isBlank(strategy)) {
            return "balanced";
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT);
        if ("balanced".equals(normalized) || "clarity_first".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "strategy 仅支持 balanced 或 clarity_first");
    }

    private int resolveRenameMaxEdits(Integer maxEdits) {
        if (maxEdits == null || maxEdits <= 0) {
            return DAILY_RENAME_MAX_EDITS_DEFAULT;
        }
        return Math.min(maxEdits, DAILY_RENAME_MAX_EDITS_MAX);
    }

    private List<Task> loadDailyReviewTasks(Long userId, List<Long> taskIds, LocalDate reviewDate) {
        if (taskIds != null && !taskIds.isEmpty()) {
            if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少包含一个有效任务ID");
            }
            Set<Long> uniqueIds = permissionService.requireAllTasksReadable(userId, taskIds);
            List<Task> selectedTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .in(Task::getId, uniqueIds)
                    .eq(Task::getIsDelete, 0)
                    .eq(Task::getDueDate, reviewDate)
                    .orderByDesc(Task::getPriority)
                    .orderByAsc(Task::getCreateTime, Task::getId));
            if (selectedTasks.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "未找到与 reviewDate 和 taskIds 匹配的任务");
            }
            Set<Long> selectedIds = selectedTasks.stream().map(Task::getId).collect(Collectors.toSet());
            if (!selectedIds.containsAll(uniqueIds) || selectedIds.size() != uniqueIds.size()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 中存在不满足 reviewDate 的任务，不能部分放行");
            }
            return selectedTasks;
        }
        List<Task> candidates = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeUserId, userId)
                .eq(Task::getDueDate, reviewDate)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreateTime, Task::getId));
        Set<Long> readableIds = candidates.isEmpty()
                ? Set.of()
                : permissionService.filterReadableTaskIds(userId, candidates.stream().map(Task::getId).toList());
        return candidates.stream().filter(task -> readableIds.contains(task.getId())).toList();
    }

    private String buildDailyReviewRenameUserPrompt(LocalDate reviewDate, String strategy, int maxEdits,
                                                    List<Task> completedTasks, List<Task> pendingTasks) {
        JSONArray completedContext = JSONUtil.createArray();
        for (Task task : completedTasks) {
            completedContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId()).set("title", task.getTitle())
                    .set("status", task.getStatus()).set("priority", task.getPriority())
                    .set("completedAt", task.getCompletedAt()));
        }
        JSONArray pendingContext = JSONUtil.createArray();
        for (Task task : pendingTasks) {
            pendingContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId()).set("title", task.getTitle())
                    .set("status", task.getStatus()).set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate()).set("description", task.getDescription()));
        }
        return "reviewDate: " + reviewDate
                + "\nstrategy: " + strategy
                + "\nmaxEdits: " + maxEdits
                + "\n已完成任务(JSON): " + completedContext
                + "\n未完成任务(JSON): " + pendingContext
                + "\n仅返回未完成任务（pendingTasks）的改名建议。";
    }

    private List<TitleRenameSuggestionItemVO> parseAndValidateRenameSuggestions(
            String aiRawContent, List<Task> pendingTasks, int maxEdits) {
        JSONObject resultObj = JSONUtil.parseObj(jsonSanitizer.sanitizeObject(aiRawContent));
        JSONArray items = resultObj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, Task> pendingTaskMap = pendingTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity(), (a, b) -> a));
        Set<Long> seenTaskIds = new HashSet<>();
        List<TitleRenameSuggestionItemVO> suggestions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (suggestions.size() >= maxEdits) {
                break;
            }
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            Long taskId = item.getLong("taskId");
            if (taskId == null || !pendingTaskMap.containsKey(taskId) || !seenTaskIds.add(taskId)) {
                continue;
            }
            Task sourceTask = pendingTaskMap.get(taskId);
            String oldTitle = safeTrim(sourceTask.getTitle());
            String newTitle = normalizeSuggestedTitle(item.getStr("newTitle"));
            if (StrUtil.isBlank(oldTitle) || StrUtil.isBlank(newTitle) || StrUtil.equals(oldTitle, newTitle)) {
                continue;
            }
            String reason = safeTrim(item.getStr("reason"));
            if (StrUtil.isBlank(reason)) {
                reason = "提升标题清晰度与可执行性";
            } else if (reason.length() > DAILY_RENAME_REASON_MAX_LEN) {
                reason = reason.substring(0, DAILY_RENAME_REASON_MAX_LEN);
            }
            Integer confidence = clamp(item.getInt("confidence") == null ? 75 : item.getInt("confidence"), 0, 100);
            TitleRenameSuggestionItemVO suggestion = new TitleRenameSuggestionItemVO();
            suggestion.setTaskId(taskId);
            suggestion.setOldTitle(oldTitle);
            suggestion.setNewTitle(newTitle);
            suggestion.setReason(reason);
            suggestion.setConfidence(confidence);
            suggestions.add(suggestion);
        }
        return suggestions;
    }

    private String normalizeSuggestedTitle(String title) {
        if (StrUtil.isBlank(title)) {
            return null;
        }
        String normalized = title.trim().replaceAll("\\s+", " ");
        if (normalized.length() > TASK_TITLE_MAX_LEN) {
            normalized = normalized.substring(0, TASK_TITLE_MAX_LEN).trim();
        }
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private List<TitleRenameSuggestionItemVO> fallbackRenameSuggestions(List<Task> pendingTasks, int maxEdits) {
        List<TitleRenameSuggestionItemVO> result = new ArrayList<>();
        for (Task task : pendingTasks) {
            if (result.size() >= maxEdits) {
                break;
            }
            String oldTitle = safeTrim(task.getTitle());
            if (StrUtil.isBlank(oldTitle)) {
                continue;
            }
            String newTitle = normalizeSuggestedTitle("下一步：" + oldTitle);
            if (StrUtil.isBlank(newTitle) || StrUtil.equals(oldTitle, newTitle)) {
                continue;
            }
            TitleRenameSuggestionItemVO item = new TitleRenameSuggestionItemVO();
            item.setTaskId(task.getId());
            item.setOldTitle(oldTitle);
            item.setNewTitle(newTitle);
            item.setReason("规则兜底：优化标题表达");
            item.setConfidence(60);
            result.add(item);
        }
        return result;
    }

    private String generateRenameOperationId(LocalDate reviewDate) {
        return reviewDate.toString().replace("-", "") + "_rename_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void saveRenameLogs(Long userId, LocalDate reviewDate, String operationId,
                                List<TitleRenameSuggestionItemVO> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (TitleRenameSuggestionItemVO suggestion : suggestions) {
            TaskTitleRenameLog logItem = new TaskTitleRenameLog();
            logItem.setOperationId(operationId);
            logItem.setUserId(userId);
            logItem.setTaskId(suggestion.getTaskId());
            logItem.setReviewDate(reviewDate);
            logItem.setOldTitle(suggestion.getOldTitle());
            logItem.setNewTitle(suggestion.getNewTitle());
            logItem.setReason(suggestion.getReason());
            logItem.setConfidence(suggestion.getConfidence());
            logItem.setIsApplied(0);
            logItem.setIsRollback(0);
            logItem.setCreateTime(now);
            logItem.setUpdateTime(now);
            taskTitleRenameLogMapper.insert(logItem);
        }
    }
}
