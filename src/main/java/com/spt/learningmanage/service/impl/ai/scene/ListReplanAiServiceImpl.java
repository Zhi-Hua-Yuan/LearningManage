package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiReplanItemMapper;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.AiReplanItem;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewItemVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.BusinessDataVersionService;
import jakarta.annotation.Resource;
import com.spt.learningmanage.service.ai.scene.ListReplanAiService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.draft.AiReplanWriteGuard;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ListReplanAiServiceImpl extends AiSceneSupport implements ListReplanAiService {

    private static final Logger log = LoggerFactory.getLogger(ListReplanAiServiceImpl.class);
    private static final int TASK_TITLE_MAX_LEN = 60;
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;
    private static final int LIST_REPLAN_TITLE_CONFIDENCE_THRESHOLD = 70;
    private static final int LIST_REPLAN_REASON_MAX_LEN = 160;
    private static final int LIST_REPLAN_PREVIEW_EXPIRE_MINUTES = 20;
    private static final Pattern DATE_TOKEN_PATTERN =
            Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}-\\d{1,2}|\\d{1,2}月\\d{1,2}日");

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final AiReplanOperationMapper aiReplanOperationMapper;
    private final AiReplanItemMapper aiReplanItemMapper;
    private final AiInvocationPipeline aiInvocationPipeline;
    private final PermissionService permissionService;
    private final AiReplanWriteGuard replanWriteGuard;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    @Resource
    private KnowledgeIndexEventPublisher knowledgeIndexEventPublisher;

    @Resource
    private BusinessDataVersionService businessDataVersionService;

    public ListReplanAiServiceImpl(TaskMapper taskMapper,
                                   ProjectMapper projectMapper,
                                   AiReplanOperationMapper aiReplanOperationMapper,
                                   AiReplanItemMapper aiReplanItemMapper,
                                   AiInvocationPipeline aiInvocationPipeline,
                                   PermissionService permissionService,
                                   AiReplanWriteGuard replanWriteGuard,
                                   AiModelSelector modelSelector,
                                   AiJsonResponseSanitizer jsonSanitizer) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.aiReplanOperationMapper = aiReplanOperationMapper;
        this.aiReplanItemMapper = aiReplanItemMapper;
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.permissionService = permissionService;
        this.replanWriteGuard = replanWriteGuard;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean replanListTasks(Long listId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (listId == null || listId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "listId 不合法");
        }
        permissionService.requireProjectManage(currentUserId, listId);

        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, listId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .last("limit 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "清单不存在或无访问权限");
        }

        List<Task> allTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, listId)
                .eq(Task::getIsDelete, 0)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getDueDate, Task::getCreateTime, Task::getId));
        if (allTasks.isEmpty()) {
            return false;
        }

        List<Task> completedTasks = allTasks.stream()
                .filter(task -> TaskStatusEnum.isCompleted(task.getStatus()))
                .toList();
        List<Task> pendingTasks = allTasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), TaskStatusEnum.TODO.getValue()))
                .toList();
        if (pendingTasks.isEmpty()) {
            syncProjectEndDateIfNeeded(listId, currentUserId, project.getEndDate());
            return false;
        }

        LocalDate today = LocalDate.now();
        String userPrompt = buildListReplanUserPrompt(project, completedTasks, pendingTasks, today);
        List<ListTaskReplanItem> replanItems = aiInvocationPipeline.execute(
                new AiExecutionCommand(
                        currentUserId, modelSelector.breakdownModel(),
                        AiPromptCodeEnum.LIST_REPLAN_PREVIEW, userPrompt, "AI 清单重排结果格式异常"
                ),
                rawContent -> parseAndValidateListReplanItems(rawContent, pendingTasks, today),
                failure -> {
                    log.warn("AI 清单重排失败，回退为不变更策略。userId={}, listId={}, type={}",
                            currentUserId, listId, failure.failureType(), failure.cause());
                    return fallbackListReplanItems(pendingTasks);
                }
        ).data();

        int updatedCount = applyListReplanItems(replanItems, currentUserId);
        syncProjectEndDateIfNeeded(listId, currentUserId, project.getEndDate());
        return updatedCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiListReplanPreviewVO previewListReplan(Long listId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (listId == null || listId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "listId 不合法");
        }
        permissionService.requireProjectManage(currentUserId, listId);

        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, listId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .last("limit 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "清单不存在或无访问权限");
        }

        List<Task> allTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, listId)
                .eq(Task::getIsDelete, 0)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getDueDate, Task::getCreateTime, Task::getId));

        List<Task> completedTasks = allTasks.stream()
                .filter(task -> TaskStatusEnum.isCompleted(task.getStatus()))
                .toList();
        List<Task> pendingTasks = allTasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), TaskStatusEnum.TODO.getValue()))
                .toList();

        LocalDate today = LocalDate.now();
        List<ListTaskReplanItem> replanItems;
        String traceId = null;
        if (pendingTasks.isEmpty()) {
            replanItems = List.of();
        } else {
            String userPrompt = buildListReplanUserPrompt(project, completedTasks, pendingTasks, today);
            String modelName = modelSelector.breakdownModel();
            var execution = aiInvocationPipeline.execute(
                    new AiExecutionCommand(
                            currentUserId, modelName, AiPromptCodeEnum.LIST_REPLAN_PREVIEW,
                            userPrompt, "AI 清单重排结果格式异常"
                    ),
                    rawContent -> parseAndValidateListReplanItems(rawContent, pendingTasks, today),
                    failure -> {
                        log.warn("AI 清单重排预览失败，回退为不变更策略。userId={}, listId={}, type={}",
                                currentUserId, listId, failure.failureType(), failure.cause());
                        return fallbackListReplanItems(pendingTasks);
                    }
            );
            replanItems = execution.data();
            traceId = execution.traceId();
        }

        String operationId = generateListReplanOperationId();
        saveListReplanPreview(operationId, currentUserId, listId, traceId, replanItems, pendingTasks);
        return buildListReplanPreviewVO(operationId, replanItems);
    }

    @Override
    public boolean confirmListReplan(Long listId, String operationId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (listId == null || listId <= 0 || StrUtil.isBlank(operationId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        String normalizedOperationId = operationId.trim();
        return replanWriteGuard.confirm(currentUserId, listId, normalizedOperationId,
                operation -> applyConfirmedReplan(currentUserId, listId, operation));
    }

    @Override
    public boolean cancelListReplan(String operationId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (StrUtil.isBlank(operationId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不能为空");
        }

        String normalizedOperationId = operationId.trim();
        return replanWriteGuard.cancel(currentUserId, normalizedOperationId);
    }

    private boolean applyConfirmedReplan(Long currentUserId, Long listId, AiReplanOperation operation) {
        permissionService.requireProjectManage(currentUserId, listId);
        List<AiReplanItem> items = aiReplanItemMapper.selectList(new LambdaQueryWrapper<AiReplanItem>()
                .eq(AiReplanItem::getOperationId, operation.getOperationId()));
        List<AiReplanItem> changedItems = items.stream()
                .filter(this::hasAnyReplanChange)
                .toList();
        if (!changedItems.isEmpty()) {
            permissionService.requireAllTasksReorganizable(currentUserId,
                    changedItems.stream().map(AiReplanItem::getTaskId).toList());
        }
        for (AiReplanItem item : changedItems) {
            int rows = taskMapper.compareAndSetReplan(
                    item.getTaskId(), listId, TaskStatusEnum.TODO.getValue(),
                    item.getOldTitle(), item.getOldPriority(), item.getOldDueDate(),
                    item.getTaskSnapshotUpdateTime(), item.getNewTitle(),
                    item.getNewPriority(), item.getNewDueDate());
            if (rows != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "任务快照已变化，重排确认失败，请重新预览");
            }
        }
        if (knowledgeIndexEventPublisher != null && !changedItems.isEmpty()) {
            knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.TASK,
                    changedItems.stream().map(AiReplanItem::getTaskId).toList(),
                    KnowledgeEventTypeEnum.SOURCE_CHANGED);
        }
        if (businessDataVersionService != null && !changedItems.isEmpty()) {
            businessDataVersionService.incrementProjectAndOwningTeam(listId);
        }

        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, listId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .last("limit 1"));
        if (project != null) {
            syncProjectEndDateIfNeeded(listId, currentUserId, project.getEndDate());
        }
        return !changedItems.isEmpty();
    }

    private boolean hasAnyReplanChange(AiReplanItem item) {
        return item != null
                && item.getTaskId() != null
                && (!Objects.equals(item.getOldTitle(), item.getNewTitle())
                || !Objects.equals(item.getOldPriority(), item.getNewPriority())
                || !Objects.equals(item.getOldDueDate(), item.getNewDueDate()));
    }

    private String buildListReplanUserPrompt(Project project, List<Task> completedTasks, List<Task> pendingTasks, LocalDate today) {
        int totalCount = completedTasks.size() + pendingTasks.size();
        int completedCount = completedTasks.size();
        int pendingCount = pendingTasks.size();
        int overduePendingCount = (int) pendingTasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today))
                .count();
        int completedOnTimeCount = (int) completedTasks.stream()
                .filter(task -> task.getCompletedAt() != null)
                .filter(task -> task.getDueDate() == null || !task.getCompletedAt().toLocalDate().isAfter(task.getDueDate()))
                .count();
        double completionRate = totalCount == 0 ? 0D : (completedCount * 100.0D / totalCount);

        JSONArray completedContext = JSONUtil.createArray();
        for (Task task : completedTasks) {
            completedContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate())
                    .set("completedAt", task.getCompletedAt())
                    .set("status", task.getStatus()));
        }

        JSONArray pendingContext = JSONUtil.createArray();
        for (Task task : pendingTasks) {
            pendingContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("description", task.getDescription())
                    .set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate())
                    .set("status", task.getStatus()));
        }

        return "清单ID: " + project.getId()
                + "\n清单名称: " + project.getName()
                + "\n清单目标: " + project.getGoal()
                + "\n今天日期: " + today
                + "\n执行指标: {"
                + "\"total\":" + totalCount
                + ",\"completed\":" + completedCount
                + ",\"pending\":" + pendingCount
                + ",\"overduePending\":" + overduePendingCount
                + ",\"completedOnTime\":" + completedOnTimeCount
                + ",\"completionRate\":" + String.format(Locale.ROOT, "%.2f", completionRate)
                + "}"
                + "\n已完成任务(JSON): " + completedContext
                + "\n未完成任务(JSON): " + pendingContext
                + "\n仅返回未完成任务（pending taskIds）的重排结果。";
    }

    private List<ListTaskReplanItem> parseAndValidateListReplanItems(String aiRawContent,
                                                                      List<Task> pendingTasks,
                                                                      LocalDate today) {
        String cleanedText = jsonSanitizer.sanitizeObject(aiRawContent);
        JSONObject resultObj = JSONUtil.parseObj(cleanedText);
        JSONArray items = resultObj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<Long, Task> pendingTaskMap = pendingTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity(), (a, b) -> a));
        Set<Long> seenTaskIds = new HashSet<>();
        List<ListTaskReplanItem> result = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
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
            String newTitle = normalizeReplanTitle(item.getStr("newTitle"), oldTitle);
            int oldPriority = sourceTask.getPriority() == null ? 0 : sourceTask.getPriority();
            int newPriority = normalizeReplanPriority(item.getInt("newPriority"), oldPriority);
            LocalDate oldDueDate = sourceTask.getDueDate();
            LocalDate newDueDate = normalizeReplanDueDate(item.get("newDueDate"), oldDueDate);
            int confidence = clamp(item.getInt("confidence") == null ? 75 : item.getInt("confidence"), 0, 100);
            String reason = normalizeReplanReason(item.getStr("reason"));

            boolean likelyStarted = oldDueDate != null && !oldDueDate.isAfter(today);
            if (likelyStarted && confidence < LIST_REPLAN_TITLE_CONFIDENCE_THRESHOLD) {
                newTitle = oldTitle;
                reason = StrUtil.isBlank(reason) ? "低置信度且疑似已开始执行，保持原标题" : reason;
            }
            reason = alignReplanReasonWithDueDateFact(reason, oldDueDate, newDueDate);

            ListTaskReplanItem replanItem = new ListTaskReplanItem();
            replanItem.setTaskId(taskId);
            replanItem.setOldTitle(oldTitle);
            replanItem.setNewTitle(newTitle);
            replanItem.setOldPriority(oldPriority);
            replanItem.setNewPriority(newPriority);
            replanItem.setOldDueDate(oldDueDate);
            replanItem.setNewDueDate(newDueDate);
            replanItem.setConfidence(confidence);
            replanItem.setReason(reason);
            result.add(replanItem);
        }

        return result;
    }

    private List<ListTaskReplanItem> fallbackListReplanItems(List<Task> pendingTasks) {
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return List.of();
        }
        List<ListTaskReplanItem> fallbackItems = new ArrayList<>(pendingTasks.size());
        for (Task task : pendingTasks) {
            ListTaskReplanItem item = new ListTaskReplanItem();
            int priority = task.getPriority() == null ? 0 : task.getPriority();
            item.setTaskId(task.getId());
            item.setOldTitle(task.getTitle());
            item.setNewTitle(task.getTitle());
            item.setOldPriority(priority);
            item.setNewPriority(priority);
            item.setOldDueDate(task.getDueDate());
            item.setNewDueDate(task.getDueDate());
            item.setConfidence(0);
            item.setReason("AI不可用，保持原计划");
            fallbackItems.add(item);
        }
        return fallbackItems;
    }

    private String generateListReplanOperationId() {
        return LocalDate.now().toString().replace("-", "")
                + "_replan_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void saveListReplanPreview(String operationId,
                                       Long userId,
                                       Long listId,
                                       String traceId,
                                       List<ListTaskReplanItem> replanItems,
                                       List<Task> pendingTasks) {
        LocalDateTime now = LocalDateTime.now();
        AiReplanOperation operation = new AiReplanOperation();
        operation.setOperationId(operationId);
        operation.setUserId(userId);
        operation.setProjectId(listId);
        operation.setTraceId(traceId);
        operation.setStatus(AiReplanWriteGuard.PREVIEW);
        operation.setExpiresAt(now.plusMinutes(LIST_REPLAN_PREVIEW_EXPIRE_MINUTES));
        operation.setCreatedAt(now);
        aiReplanOperationMapper.insert(operation);

        if (replanItems == null || replanItems.isEmpty()) {
            return;
        }
        Map<Long, Task> pendingMap = pendingTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity(), (a, b) -> a));
        for (ListTaskReplanItem item : replanItems) {
            if (item == null || item.getTaskId() == null) {
                continue;
            }
            AiReplanItem entity = new AiReplanItem();
            entity.setOperationId(operationId);
            entity.setTaskId(item.getTaskId());
            entity.setOldTitle(item.getOldTitle());
            entity.setNewTitle(item.getNewTitle());
            entity.setOldPriority(item.getOldPriority());
            entity.setNewPriority(item.getNewPriority());
            entity.setOldDueDate(item.getOldDueDate());
            entity.setNewDueDate(item.getNewDueDate());
            entity.setConfidence(item.getConfidence());
            entity.setReason(item.getReason());
            Task sourceTask = pendingMap.get(item.getTaskId());
            entity.setTaskSnapshotUpdateTime(sourceTask == null ? null : sourceTask.getUpdateTime());
            aiReplanItemMapper.insert(entity);
        }
    }

    private AiListReplanPreviewVO buildListReplanPreviewVO(String operationId, List<ListTaskReplanItem> replanItems) {
        List<ListTaskReplanItem> safeItems = replanItems == null ? List.of() : replanItems;
        AiListReplanPreviewVO result = new AiListReplanPreviewVO();
        result.setOperationId(operationId);
        result.setChangedCount((int) safeItems.stream().filter(ListTaskReplanItem::hasAnyChange).count());

        List<AiListReplanPreviewItemVO> previewTasks = new ArrayList<>(safeItems.size());
        for (ListTaskReplanItem item : safeItems) {
            AiListReplanPreviewItemVO previewItem = new AiListReplanPreviewItemVO();
            previewItem.setTaskId(item.getTaskId());
            previewItem.setOldTitle(item.getOldTitle());
            previewItem.setNewTitle(item.getNewTitle());
            previewItem.setOldPriority(item.getOldPriority());
            previewItem.setNewPriority(item.getNewPriority());
            previewItem.setOldDueDate(item.getOldDueDate());
            previewItem.setNewDueDate(item.getNewDueDate());
            previewItem.setDueChanged(!Objects.equals(item.getOldDueDate(), item.getNewDueDate()));
            previewItem.setDueDeltaDays(calculateDueDeltaDays(item.getOldDueDate(), item.getNewDueDate()));
            previewItem.setDueChangeLabel(buildDueChangeLabel(item.getOldDueDate(), item.getNewDueDate()));
            previewItem.setConfidence(item.getConfidence());
            previewItem.setReason(item.getReason());
            previewTasks.add(previewItem);
        }
        result.setPreviewTasks(previewTasks);
        return result;
    }

    private ListTaskReplanItem toListReplanItem(AiReplanItem item) {
        ListTaskReplanItem replanItem = new ListTaskReplanItem();
        replanItem.setTaskId(item.getTaskId());
        replanItem.setOldTitle(item.getOldTitle());
        replanItem.setNewTitle(item.getNewTitle());
        replanItem.setOldPriority(item.getOldPriority());
        replanItem.setNewPriority(item.getNewPriority());
        replanItem.setOldDueDate(item.getOldDueDate());
        replanItem.setNewDueDate(item.getNewDueDate());
        replanItem.setConfidence(item.getConfidence());
        replanItem.setReason(item.getReason());
        return replanItem;
    }

    private int applyListReplanItems(List<ListTaskReplanItem> replanItems, Long userId) {
        if (replanItems == null || replanItems.isEmpty()) {
            return 0;
        }

        int updatedCount = 0;
        for (ListTaskReplanItem item : replanItems) {
            if (item == null || item.getTaskId() == null || !item.hasAnyChange()) {
                continue;
            }

            int rows = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, item.getTaskId())
                    .eq(Task::getStatus, TaskStatusEnum.TODO.getValue())
                    .eq(Task::getIsDelete, 0)
                    .set(Task::getTitle, item.getNewTitle())
                    .set(Task::getPriority, item.getNewPriority())
                    .set(Task::getDueDate, item.getNewDueDate()));
            if (rows == 1) {
                updatedCount++;
            }
        }

        return updatedCount;
    }

    private void syncProjectEndDateIfNeeded(Long listId, Long userId, LocalDate currentProjectEndDate) {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, listId)
                .eq(Task::getIsDelete, 0));

        LocalDate maxDueDate = tasks.stream()
                .map(Task::getDueDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (maxDueDate == null || Objects.equals(currentProjectEndDate, maxDueDate)) {
            return;
        }

        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, listId)
                .isNull(Project::getDeletedAt)
                .set(Project::getEndDate, maxDueDate));
    }

    private String normalizeReplanTitle(String suggestedTitle, String oldTitle) {
        String oldValue = StrUtil.blankToDefault(safeTrim(oldTitle), "");
        if (StrUtil.isBlank(suggestedTitle)) {
            return oldValue;
        }
        String normalized = suggestedTitle.trim();
        if (normalized.length() > TASK_TITLE_MAX_LEN) {
            normalized = normalized.substring(0, TASK_TITLE_MAX_LEN);
        }
        return StrUtil.isBlank(normalized) ? oldValue : normalized;
    }

    private int normalizeReplanPriority(Integer suggestedPriority, int oldPriority) {
        if (suggestedPriority == null) {
            return clamp(oldPriority, TASK_PRIORITY_MIN, TASK_PRIORITY_MAX);
        }
        return clamp(suggestedPriority, TASK_PRIORITY_MIN, TASK_PRIORITY_MAX);
    }

    private LocalDate normalizeReplanDueDate(Object suggestedDueDate, LocalDate oldDueDate) {
        if (suggestedDueDate == null) {
            return oldDueDate;
        }

        String dueDateText = safeTrim(String.valueOf(suggestedDueDate));
        if (StrUtil.isBlank(dueDateText)) {
            return oldDueDate;
        }
        if ("null".equalsIgnoreCase(dueDateText) || "none".equalsIgnoreCase(dueDateText)) {
            return null;
        }
        try {
            return LocalDate.parse(dueDateText);
        } catch (Exception e) {
            return oldDueDate;
        }
    }

    private String normalizeReplanReason(String reason) {
        String normalized = safeTrim(reason);
        if (StrUtil.isBlank(normalized)) {
            return "按执行力与任务属性动态重排";
        }
        return normalized.length() > LIST_REPLAN_REASON_MAX_LEN
                ? normalized.substring(0, LIST_REPLAN_REASON_MAX_LEN)
                : normalized;
    }

    private String alignReplanReasonWithDueDateFact(String reason, LocalDate oldDueDate, LocalDate newDueDate) {
        String normalized = normalizeReplanReason(reason);
        String stripped = stripDueDateRelatedClauses(normalized);
        String fact = buildDueDateFact(oldDueDate, newDueDate);
        String merged = StrUtil.isBlank(stripped) ? fact : stripped + "；" + fact;
        return merged.length() > LIST_REPLAN_REASON_MAX_LEN
                ? merged.substring(0, LIST_REPLAN_REASON_MAX_LEN)
                : merged;
    }

    private String stripDueDateRelatedClauses(String reason) {
        if (StrUtil.isBlank(reason)) {
            return "";
        }
        String[] parts = reason.split("[。；]");
        List<String> kept = new ArrayList<>(parts.length);
        for (String part : parts) {
            String text = safeTrim(part);
            if (StrUtil.isBlank(text)) {
                continue;
            }
            if (containsDueDateHint(text)) {
                continue;
            }
            kept.add(text);
        }
        return String.join("；", kept);
    }

    private boolean containsDueDateHint(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        if (DATE_TOKEN_PATTERN.matcher(text).find()) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("due")
                || text.contains("截止")
                || text.contains("到期")
                || text.contains("提前")
                || text.contains("顺延")
                || text.contains("前移")
                || text.contains("后移")
                || text.contains("延期");
    }

    private String buildDueDateFact(LocalDate oldDueDate, LocalDate newDueDate) {
        if (Objects.equals(oldDueDate, newDueDate)) {
            return "截止日期不变";
        }
        if (oldDueDate == null && newDueDate != null) {
            return "新增截止日期为" + newDueDate;
        }
        if (oldDueDate != null && newDueDate == null) {
            return "移除截止日期（原为" + oldDueDate + "）";
        }
        long delta = ChronoUnit.DAYS.between(oldDueDate, newDueDate);
        if (delta > 0) {
            return "截止日期顺延" + delta + "天（" + oldDueDate + " -> " + newDueDate + "）";
        }
        if (delta < 0) {
            return "截止日期提前" + (-delta) + "天（" + oldDueDate + " -> " + newDueDate + "）";
        }
        return "截止日期不变";
    }

    private Integer calculateDueDeltaDays(LocalDate oldDueDate, LocalDate newDueDate) {
        if (oldDueDate == null || newDueDate == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(oldDueDate, newDueDate));
    }

    private String buildDueChangeLabel(LocalDate oldDueDate, LocalDate newDueDate) {
        if (Objects.equals(oldDueDate, newDueDate)) {
            return "截止日期不变";
        }
        if (oldDueDate == null && newDueDate != null) {
            return "新增截止日期";
        }
        if (oldDueDate != null && newDueDate == null) {
            return "移除截止日期";
        }
        Integer delta = calculateDueDeltaDays(oldDueDate, newDueDate);
        if (delta == null) {
            return null;
        }
        if (delta > 0) {
            return "顺延" + delta + "天";
        }
        if (delta < 0) {
            return "提前" + (-delta) + "天";
        }
        return "截止日期不变";
    }

    private static class ListTaskReplanItem {
        private Long taskId;
        private String oldTitle;
        private String newTitle;
        private Integer oldPriority;
        private Integer newPriority;
        private LocalDate oldDueDate;
        private LocalDate newDueDate;
        private Integer confidence;
        private String reason;

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getOldTitle() {
            return oldTitle;
        }

        public void setOldTitle(String oldTitle) {
            this.oldTitle = oldTitle;
        }

        public String getNewTitle() {
            return newTitle;
        }

        public void setNewTitle(String newTitle) {
            this.newTitle = newTitle;
        }

        public Integer getOldPriority() {
            return oldPriority;
        }

        public void setOldPriority(Integer oldPriority) {
            this.oldPriority = oldPriority;
        }

        public Integer getNewPriority() {
            return newPriority;
        }

        public void setNewPriority(Integer newPriority) {
            this.newPriority = newPriority;
        }

        public LocalDate getOldDueDate() {
            return oldDueDate;
        }

        public void setOldDueDate(LocalDate oldDueDate) {
            this.oldDueDate = oldDueDate;
        }

        public LocalDate getNewDueDate() {
            return newDueDate;
        }

        public void setNewDueDate(LocalDate newDueDate) {
            this.newDueDate = newDueDate;
        }

        public Integer getConfidence() {
            return confidence;
        }

        public void setConfidence(Integer confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public boolean hasAnyChange() {
            return !Objects.equals(oldTitle, newTitle)
                    || !Objects.equals(oldPriority, newPriority)
                    || !Objects.equals(oldDueDate, newDueDate);
        }
    }

}
