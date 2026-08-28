package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.mapper.AiReplanItemMapper;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.AiTodayOrderRequest;
import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.entity.AiReplanItem;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskTitleRenameLog;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewItemVO;
import com.spt.learningmanage.model.vo.ai.AiListReplanPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiPolishPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiTaskOrderItemVO;
import com.spt.learningmanage.model.vo.ai.AiTodayOrderVO;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;
import com.spt.learningmanage.model.vo.ai.TitleRenameSuggestionItemVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.service.AiService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private static final int MAX_POLISH_TASK_COUNT = 50;
    private static final String EMPTY_REFLECTION_PLACEHOLDER = "（用户未填写反思）";

    private static final int PROJECT_NAME_MAX_LEN = 100;
    private static final int TASK_TITLE_MAX_LEN = 60;
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;
    private static final int TODAY_ORDER_LIMIT_MAX = 50;
    private static final int TODAY_ORDER_LIMIT_DEFAULT = 20;
    private static final int DAILY_RENAME_MAX_EDITS_DEFAULT = 10;
    private static final int DAILY_RENAME_MAX_EDITS_MAX = 50;
    private static final int DAILY_RENAME_REASON_MAX_LEN = 120;
    private static final int LIST_REPLAN_TITLE_CONFIDENCE_THRESHOLD = 70;
    private static final int LIST_REPLAN_REASON_MAX_LEN = 160;
    private static final int LIST_REPLAN_PREVIEW_EXPIRE_MINUTES = 20;
    private static final int LIST_REPLAN_STATUS_PREVIEW = 0;
    private static final int LIST_REPLAN_STATUS_CONFIRMED = 1;
    private static final int LIST_REPLAN_STATUS_CANCELED = 2;
    private static final int LIST_REPLAN_STATUS_EXPIRED = 3;
    private static final int AI_DRAFT_EXPIRE_MINUTES = 20;
    private static final Pattern DATE_TOKEN_PATTERN = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}-\\d{1,2}|\\d{1,2}月\\d{1,2}日");

    @Resource
    private AiProperties aiProperties;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TaskTitleRenameLogMapper taskTitleRenameLogMapper;
    @Resource
    private AiDraftMapper aiDraftMapper;
    @Resource
    private AiDraftConfirmLogMapper aiDraftConfirmLogMapper;
    @Resource
    private MilestoneMapper milestoneMapper;
    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private AiReplanOperationMapper aiReplanOperationMapper;

    @Resource
    private AiReplanItemMapper aiReplanItemMapper;

    @Resource
    private AiCallLogService aiCallLogService;

    @Resource
    private AiModelClient aiModelClient;

    @Resource
    private PromptTemplateResolver promptTemplateResolver;

    @Resource
    private AiInvocationPipeline aiInvocationPipeline;

    @Resource
    private PermissionService permissionService;

    @Resource
    private TaskCreationService taskCreationService;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (StrUtil.isBlank(systemPrompt) || StrUtil.isBlank(userPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
        try {
            return aiModelClient.invoke(resolveModel(aiProperties.getModel()), systemPrompt, userPrompt).content();
        } catch (AiInvocationException e) {
            log.warn("AI 通用对话调用失败: type={}, model={}", e.getFailureType(), e.getModelName(), e);
            throw toBusinessException(e);
        }
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration, boolean detailed) {
        if (StrUtil.hasBlank(target, duration)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空，描述可为空");
        }
        Long userId = getCurrentUserId();

        String normalizedTarget = target.trim();
        if (normalizedTarget.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标长度不能超过100个字符");
        }

        String today = LocalDate.now().toString();
        String userPrompt = String.format("目标：%s，周期：%s，今天日期：%s。", normalizedTarget, duration.trim(), today);
        if (StrUtil.isNotBlank(description)) {
            userPrompt = userPrompt + String.format("补充描述：%s。", description.trim());
        }

        // 获取提示词模版编码
        AiPromptCodeEnum promptCode = detailed
                ? AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED
                : AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT;
        // 根据提示词编码去获取提示词模版
        AiPromptTemplate promptTemplate = promptTemplateResolver.resolve(promptCode);
        String systemPrompt = promptTemplate.systemPrompt();
        String modelName = resolveModel(aiProperties.getBreakdownModel());
        // 创建 AI 调用日志
        Long callLogId = createAiCallLogSafely(
                userId,
                modelName,
                promptTemplate,
                buildAiCallRequestText(systemPrompt, userPrompt),
                0
        );

        long startTime = System.currentTimeMillis();
        String aiRawContent;
        try {
            // 调用 AI 模型，并获取结果
            aiRawContent = invokeAiWithLog(callLogId, modelName, systemPrompt, userPrompt, startTime).content();
        } catch (AiInvocationException e) {
            log.warn("AI 任务拆解调用失败: type={}, model={}", e.getFailureType(), e.getModelName(), e);
            throw toBusinessException(e);
        }

        try {
            // 解析 AI 返回结果
            String jsonText = sanitizeJsonArrayText(aiRawContent);
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            List<MilestoneDraftVO> result = JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
            // 检查每一个里程碑是否符合条件
            normalizeAndValidateDrafts(result);
            // 检测结果风险并在有问题时记录日志
            logDraftLengthRisk(result, normalizedTarget, detailed);
            if (result == null || result.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.OPERATION_ERROR,
                        "AI 未生成可用草稿，请调整描述后重试（避免与名称长度约束冲突）"
                );
            }
            // AI 模型调用成功记录日志
            markAiCallSuccessSafely(callLogId, aiRawContent, elapsedSince(startTime));
            return result;
        } catch (BusinessException e) {
            markAiCallParseFailedSafely(callLogId, aiRawContent,
                    "AI 任务拆解结果格式异常", elapsedSince(startTime));
            log.warn("AI 任务拆解结果校验失败", e);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 任务拆解结果格式异常，请重试");
        } catch (Exception e) {
            markAiCallParseFailedSafely(callLogId, aiRawContent, "AI 任务拆解结果格式异常", elapsedSince(startTime));
            log.warn("AI 任务拆解结果解析失败", e);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 任务拆解结果格式异常，请重试");
        }
    }

    @Override
    public String polishWeeklyReview(List<Long> taskIds, String reflection) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }

        List<Long> validTaskIds = taskIds == null
                ? new ArrayList<>()
                : taskIds.stream().filter(id -> id != null && id > 0).collect(Collectors.toCollection(ArrayList::new));

        if (taskIds != null && taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少需要包含一个有效的正整数ID");
        }

        if (validTaskIds.isEmpty()) {
            return JSONUtil.createObj()
                    .set("review", "本周暂无已完成任务记录。你可以先从最小可执行任务开始，逐步恢复节奏。")
                    .toString();
        }

        Set<Long> uniqueTaskIds = permissionService.requireAllTasksReadable(currentUserId, validTaskIds);
        List<Task> taskList = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getId, uniqueTaskIds)
                .eq(Task::getIsDelete, 0)
                .orderByDesc(Task::getCompletedAt, Task::getUpdateTime, Task::getId));

        if (taskList.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "传入的任务均不存在或无访问权限，请确认任务ID是否属于当前登录账号");
        }

        Set<Long> foundIds = taskList.stream().map(Task::getId).collect(Collectors.toSet());
        List<Long> missingIds = uniqueTaskIds.stream().filter(id -> !foundIds.contains(id)).toList();

        int actualTaskCount = taskList.size();
        List<Task> limitedTaskList = taskList.stream().limit(MAX_POLISH_TASK_COUNT).toList();

        Set<Long> projectIds = limitedTaskList.stream()
                .map(Task::getProjectId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, Project> projectMap = projectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getId, projectIds)
                        .eq(Project::getUserId, currentUserId))
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));

        JSONArray taskContext = JSONUtil.createArray();
        for (Task task : limitedTaskList) {
            Project project = projectMap.get(task.getProjectId());
            taskContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("taskTitle", task.getTitle())
                    .set("taskDescription", task.getDescription())
                    .set("status", task.getStatus())
                    .set("dueDate", task.getDueDate())
                    .set("completedAt", task.getCompletedAt())
                    .set("projectId", task.getProjectId())
                    .set("projectName", project == null ? "未识别项目" : project.getName()));
        }

        String reflectionText = StrUtil.blankToDefault(reflection, EMPTY_REFLECTION_PLACEHOLDER);

        String userPrompt = "本周完成任务数（后端计算）：" + actualTaskCount
                + "\n本周任务明细（JSON）：" + taskContext
                + "\n任务ID缺失或无权限数量：" + missingIds.size()
                + "\n缺失任务ID（仅供参考）：" + missingIds
                + "\n用户主观反思：" + reflectionText;

        AiPromptTemplate promptTemplate = promptTemplateResolver
                .resolve(AiPromptCodeEnum.WEEKLY_POLISH_DEFAULT);
        String systemPrompt = promptTemplate.systemPrompt();
        String modelName = resolveModel(aiProperties.getPolishModel());
        Long callLogId = createAiCallLogSafely(
                currentUserId,
                modelName,
                promptTemplate,
                buildAiCallRequestText(systemPrompt, userPrompt),
                0
        );

        long startTime = System.currentTimeMillis();
        String aiRawContent;
        try {
            aiRawContent = invokeAiWithLog(callLogId, modelName, systemPrompt, userPrompt, startTime).content();
        } catch (AiInvocationException e) {
            log.warn("AI 周总结润色调用失败: type={}, model={}", e.getFailureType(), e.getModelName(), e);
            throw toBusinessException(e);
        }

        try {
            String cleanedResult = sanitizeJsonObjectText(aiRawContent);
            JSONObject resultObj = JSONUtil.parseObj(cleanedResult);
            String review = resultObj.getStr("review");
            if (StrUtil.isBlank(review)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结润色结果缺少 review 字段，请重试");
            }
            // 只返回 review，确保前后端契约稳定且无多余字段。
            String result = JSONUtil.createObj().set("review", review).toString();
            markAiCallSuccessSafely(callLogId, aiRawContent, elapsedSince(startTime));
            return result;
        } catch (BusinessException e) {
            markAiCallParseFailedSafely(callLogId, aiRawContent,
                    "AI 周总结润色结果格式异常", elapsedSince(startTime));
            log.warn("AI 周总结润色结果校验失败", e);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 周总结润色结果格式异常，请重试");
        } catch (Exception e) {
            markAiCallParseFailedSafely(callLogId, aiRawContent, "AI 周总结润色结果格式异常", elapsedSince(startTime));
            log.warn("AI 周总结润色结果解析失败", e);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 周总结润色结果格式异常，请重试");
        }
    }

    @Override
    public AiBreakdownPreviewVO previewTaskBreakdown(AiBreakdownRequest request) {
        // 详细模式与默认模式
        boolean detailed = request.getDetailed() != null && request.getDetailed();
        List<MilestoneDraftVO> drafts = generateTaskBreakdown(
                request.getTarget(),
                request.getDescription(),
                request.getDuration(),
                detailed
        );
        Long userId = getCurrentUserId();
        JSONObject payload = JSONUtil.createObj()
                .set("target", request.getTarget())
                .set("description", request.getDescription())
                .set("duration", request.getDuration())
                .set("detailed", detailed)
                .set("milestones", drafts);
        AiDraft draft = createDraft(userId, AiSceneEnum.TASK_BREAKDOWN.getCode(), payload.toString(), buildInputHash(payload.toString()));
        AiBreakdownPreviewVO vo = new AiBreakdownPreviewVO();
        vo.setDraftId(draft.getDraftId());
        vo.setExpireAt(draft.getExpireAt());
        vo.setMilestones(drafts);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDraftConfirmVO confirmTaskBreakdown(String draftId, String operationId, String projectName, String projectGoal) {
        Long userId = getCurrentUserId();
        AiDraft draft = getDraftByUserAndScene(userId, draftId, AiSceneEnum.TASK_BREAKDOWN.getCode());
        // 查询 operationId 是否已经处理
        AiDraftConfirmLog replay = getConfirmLog(userId, draftId, operationId);
        if (replay != null) {
            return buildConfirmVO(true, replay.getBusinessId());
        }
        validateDraftCanConfirm(draft);

        JSONObject payload = JSONUtil.parseObj(draft.getPayloadJson());
        JSONArray milestonesJson = payload.getJSONArray("milestones");
        if (milestonesJson == null || milestonesJson.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿缺少里程碑数据");
        }
        List<MilestoneDraftVO> milestoneDrafts = JSONUtil.toList(milestonesJson, MilestoneDraftVO.class);
        normalizeAndValidateDrafts(milestoneDrafts);

        String target = safeTrim(payload.getStr("target"));
        String finalProjectName = StrUtil.isNotBlank(projectName) ? projectName.trim() : target;
        if (StrUtil.isBlank(finalProjectName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectName 不能为空");
        }
        if (finalProjectName.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectName 过长");
        }

        Project project = new Project();
        project.setUserId(userId);
        project.setName(finalProjectName);
        project.setGoal(StrUtil.isNotBlank(projectGoal) ? projectGoal.trim() : safeTrim(payload.getStr("description")));
        project.setStatus(ProjectConstant.STATUS_ACTIVE);
        project.setProgress(java.math.BigDecimal.ZERO);
        project.setIsDelete(0);
        project.setOrderNo(getNextProjectOrderNo(userId));
        int projectRows = projectMapper.insert(project);
        if (projectRows != 1 || project.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建项目失败");
        }
        ProjectAccessScope projectScope = permissionService.requireProjectCreateTask(userId, project.getId());

        int milestoneOrder = 1;
        for (MilestoneDraftVO milestoneDraft : milestoneDrafts) {
            Milestone milestone = new Milestone();
            milestone.setProjectId(project.getId());
            milestone.setUserId(userId);
            milestone.setName(milestoneDraft.getName());
            milestone.setOrderNo(milestoneOrder++);
            milestone.setProgress(java.math.BigDecimal.ZERO);
            milestone.setIsDelete(0);
            milestone.setDeleteSource(DeleteSourceConstant.NORMAL);
            int milestoneRows = milestoneMapper.insert(milestone);
            if (milestoneRows != 1 || milestone.getId() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建里程碑失败");
            }

            for (TaskDraftVO taskDraft : milestoneDraft.getTasks()) {
                Task task = new Task();
                task.setProjectId(project.getId());
                task.setMilestoneId(milestone.getId());
                task.setTitle(taskDraft.getName());
                task.setPriority(taskDraft.getPriority());
                task.setStatus(TaskStatusEnum.TODO.getValue());
                task.setDueDate(LocalDate.parse(taskDraft.getDueDate()));
                task.setIsDelete(0);
                task.setDeleteSource(DeleteSourceConstant.NORMAL);
                taskCreationService.createTask(task, projectScope, null);
            }
        }

        markDraftConfirmed(draft.getId());
        insertConfirmLog(userId, draftId, operationId, AiSceneEnum.TASK_BREAKDOWN.getCode(), project.getId());
        return buildConfirmVO(false, project.getId());
    }

    @Override
    public boolean cancelDraft(String draftId, String scene) {
        Long userId = getCurrentUserId();
        AiDraft draft = getDraftByUserAndOptionalScene(userId, draftId, scene);
        refreshDraftExpiredIfNecessary(draft);

        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            return true;
        }
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已确认，不能取消");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，不能取消");
        }
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿状态异常，不能取消");
        }

        LambdaUpdateWrapper<AiDraft> wrapper = new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getId, draft.getId())
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .set(AiDraft::getStatus, AiDraftStatusEnum.CANCELED.getValue())
                .set(AiDraft::getCanceledAt, LocalDateTime.now());
        return aiDraftMapper.update(null, wrapper) > 0;
    }

    @Override
    public AiPolishPreviewVO previewWeeklyPolish(AiPolishRequest request) {
        String polished = polishWeeklyReview(request.getTaskIds(), request.getReflection());
        Long userId = getCurrentUserId();
        JSONObject payload = JSONUtil.createObj()
                .set("taskIds", request.getTaskIds())
                .set("reflection", request.getReflection())
                .set("polished", polished);
        AiDraft draft = createDraft(userId, AiSceneEnum.WEEKLY_POLISH.getCode(), payload.toString(), buildInputHash(payload.toString()));

        AiPolishPreviewVO vo = new AiPolishPreviewVO();
        vo.setDraftId(draft.getDraftId());
        vo.setExpireAt(draft.getExpireAt());
        vo.setReview(extractReviewText(polished));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDraftConfirmVO confirmWeeklyPolish(String draftId, String operationId, Long reviewId) {
        Long userId = getCurrentUserId();
        AiDraft draft = getDraftByUserAndScene(userId, draftId, AiSceneEnum.WEEKLY_POLISH.getCode());
        AiDraftConfirmLog replay = getConfirmLog(userId, draftId, operationId);
        if (replay != null) {
            return buildConfirmVO(true, replay.getBusinessId());
        }
        validateDraftCanConfirm(draft);

        WeeklyReview review = weeklyReviewMapper.selectById(reviewId);
        if (review == null || !Objects.equals(review.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该周总结");
        }
        JSONObject payload = JSONUtil.parseObj(draft.getPayloadJson());
        String polished = payload.getStr("polished");
        String reviewText = extractReviewText(polished);
        if (StrUtil.isBlank(reviewText)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿内容为空");
        }
        review.setReflection(reviewText);
        int rows = weeklyReviewMapper.updateById(review);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结更新失败");
        }

        markDraftConfirmed(draft.getId());
        insertConfirmLog(userId, draftId, operationId, AiSceneEnum.WEEKLY_POLISH.getCode(), reviewId);
        return buildConfirmVO(false, reviewId);
    }

    @Override
    public AiDraftDetailVO getDraftDetail(String draftId) {
        Long userId = getCurrentUserId();
        if (StrUtil.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId 不能为空");
        }
        AiDraft draft = aiDraftMapper.selectOne(new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, draftId.trim())
                .eq(AiDraft::getUserId, userId)
                .last("limit 1"));
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }
        refreshDraftExpiredIfNecessary(draft);
        AiDraftDetailVO vo = new AiDraftDetailVO();
        vo.setDraftId(draft.getDraftId());
        vo.setScene(draft.getScene());
        vo.setStatus(draft.getStatus());
        vo.setStatusText(AiDraftStatusEnum.getText(draft.getStatus()));
        vo.setPayloadJson(draft.getPayloadJson());
        vo.setExpireAt(draft.getExpireAt());
        vo.setConfirmedAt(draft.getConfirmedAt());
        vo.setCanceledAt(draft.getCanceledAt());
        return vo;
    }

    @Override
    public int expirePreviewDrafts() {
        return aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .lt(AiDraft::getExpireAt, LocalDateTime.now())
                .set(AiDraft::getStatus, AiDraftStatusEnum.EXPIRED.getValue()));
    }

    @Override
    public AiTodayOrderVO recommendTodayOrder(AiTodayOrderRequest request) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }

        AiTodayOrderRequest safeRequest = request == null ? new AiTodayOrderRequest() : request;
        String strategy = normalizeStrategy(safeRequest.getStrategy());
        ZoneId zoneId = resolveZoneId(safeRequest.getTimezone());
        LocalDateTime now = resolveNow(safeRequest.getNow(), zoneId);
        int limit = resolveLimit(safeRequest.getLimit());
        LocalDate today = now.toLocalDate();

        List<Task> tasks = loadTodayTodoTasks(currentUserId, safeRequest, today, limit);

        AiTodayOrderVO result = new AiTodayOrderVO();
        result.setStrategy(strategy);
        result.setGeneratedAt(LocalDateTime.now(zoneId).toString());
        if (tasks.isEmpty()) {
            result.setFallbackUsed(false);
            result.setItems(List.of());
            return result;
        }

        String userPrompt = buildTodayOrderUserPrompt(tasks, strategy, now, today, zoneId);
        String modelName = resolveModel(aiProperties.getBreakdownModel());
        AiExecutionCommand command = new AiExecutionCommand(
                currentUserId,
                modelName,
                AiPromptCodeEnum.TODAY_ORDER_DEFAULT,
                userPrompt,
                "AI 今日任务排序结果格式异常"
        );

        try {
            AiTodayOrderVO aiResult = aiInvocationPipeline.execute(
                    command,
                    rawContent -> parseAndValidateTodayOrderResult(rawContent, tasks, strategy, now)
            ).data();
            aiResult.setGeneratedAt(LocalDateTime.now(zoneId).toString());
            aiResult.setFallbackUsed(false);
            return aiResult;
        } catch (AiInvocationException e) {
            log.warn("AI今日任务排序失败，回退规则排序: userId={}, today={}, strategy={}, type={}, model={}",
                    currentUserId, today, strategy, e.getFailureType(), e.getModelName(), e);
            result.setFallbackUsed(true);
            result.setItems(fallbackByRule(tasks, strategy, now));
            return result;
        } catch (AiResponseProcessingException e) {
            log.warn("AI今日任务排序结果解析失败，回退规则排序: userId={}, today={}, strategy={}",
                    currentUserId, today, strategy, e);
            result.setFallbackUsed(true);
            result.setItems(fallbackByRule(tasks, strategy, now));
            return result;
        }
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
                .filter(task -> TaskStatusEnum.isCompleted(task.getStatus()))
                .toList();
        List<Task> pendingTasks = dayTasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), TaskStatusEnum.TODO.getValue()))
                .toList();

        String operationId = generateRenameOperationId(reviewDate);
        DailyReviewSuggestRenameVO result = new DailyReviewSuggestRenameVO();
        result.setOperationId(operationId);
        result.setGeneratedAt(LocalDateTime.now().toString());
        result.setReviewDate(reviewDate.toString());
        if (pendingTasks.isEmpty()) {
            result.setItems(List.of());
            return result;
        }

        String userPrompt = buildDailyReviewRenameUserPrompt(reviewDate, strategy, maxEdits, completedTasks, pendingTasks);
        String modelName = resolveModel(aiProperties.getBreakdownModel());
        AiExecutionCommand command = new AiExecutionCommand(
                currentUserId,
                modelName,
                AiPromptCodeEnum.DAILY_REVIEW_RENAME_DEFAULT,
                userPrompt,
                "AI 日报回顾改名结果格式异常"
        );

        List<TitleRenameSuggestionItemVO> suggestions;
        try {
            suggestions = aiInvocationPipeline.execute(
                    command,
                    rawContent -> parseAndValidateRenameSuggestions(rawContent, pendingTasks, maxEdits)
            ).data();
        } catch (AiInvocationException e) {
            log.warn("AI 日报回顾改名失败，回退规则生成。userId={}, reviewDate={}, type={}, model={}",
                    currentUserId, reviewDate, e.getFailureType(), e.getModelName(), e);
            suggestions = fallbackRenameSuggestions(pendingTasks, maxEdits);
        } catch (AiResponseProcessingException e) {
            log.warn("AI 日报回顾改名结果解析失败，回退规则生成。userId={}, reviewDate={}", currentUserId, reviewDate, e);
            suggestions = fallbackRenameSuggestions(pendingTasks, maxEdits);
        }

        saveRenameLogs(currentUserId, reviewDate, operationId, suggestions);
        result.setItems(suggestions);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
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
        List<ListTaskReplanItem> replanItems;
        try {
            String userPrompt = buildListReplanUserPrompt(project, completedTasks, pendingTasks, today);
            AiPromptTemplate promptTemplate = promptTemplateResolver
                    .resolve(AiPromptCodeEnum.LIST_REPLAN_PREVIEW);
            String aiRawContent = aiModelClient.invoke(
                    resolveModel(aiProperties.getBreakdownModel()), promptTemplate.systemPrompt(), userPrompt).content();
            replanItems = parseAndValidateListReplanItems(aiRawContent, pendingTasks, today);
        } catch (Exception e) {
            log.warn("AI 清单重排失败，回退为不变更策略。userId={}, listId={}", currentUserId, listId, e);
            replanItems = fallbackListReplanItems(pendingTasks);
        }

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
        if (pendingTasks.isEmpty()) {
            replanItems = List.of();
        } else {
            String userPrompt = buildListReplanUserPrompt(project, completedTasks, pendingTasks, today);
            AiPromptTemplate promptTemplate = promptTemplateResolver
                    .resolve(AiPromptCodeEnum.LIST_REPLAN_PREVIEW);
            String systemPrompt = promptTemplate.systemPrompt();
            String modelName = resolveModel(aiProperties.getBreakdownModel());
            Long callLogId = createAiCallLogSafely(
                    currentUserId,
                    modelName,
                    promptTemplate,
                    buildAiCallRequestText(systemPrompt, userPrompt),
                    0
            );

            long startTime = System.currentTimeMillis();
            try {
                String aiRawContent = invokeAiWithLog(
                        callLogId, modelName, systemPrompt, userPrompt, startTime).content();
                try {
                    replanItems = parseAndValidateListReplanItems(aiRawContent, pendingTasks, today);
                    markAiCallSuccessSafely(callLogId, aiRawContent, elapsedSince(startTime));
                } catch (Exception e) {
                    markAiCallParseFailedSafely(callLogId, aiRawContent,
                            "AI 清单重排结果格式异常", elapsedSince(startTime));
                    log.warn("AI 清单重排预览结果解析失败，回退为不变更策略。userId={}, listId={}", currentUserId, listId, e);
                    replanItems = fallbackListReplanItems(pendingTasks);
                }
            } catch (AiInvocationException e) {
                log.warn("AI 清单重排预览失败，回退为不变更策略。userId={}, listId={}, type={}, model={}",
                        currentUserId, listId, e.getFailureType(), e.getModelName(), e);
                replanItems = fallbackListReplanItems(pendingTasks);
            }
        }

        String operationId = generateListReplanOperationId();
        saveListReplanPreview(operationId, currentUserId, listId, replanItems, pendingTasks);
        return buildListReplanPreviewVO(operationId, replanItems);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmListReplan(Long listId, String operationId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (listId == null || listId <= 0 || StrUtil.isBlank(operationId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        permissionService.requireProjectManage(currentUserId, listId);

        String normalizedOperationId = operationId.trim();
        AiReplanOperation operation = aiReplanOperationMapper.selectOne(new LambdaQueryWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getOperationId, normalizedOperationId)
                .eq(AiReplanOperation::getUserId, currentUserId)
                .eq(AiReplanOperation::getProjectId, listId)
                .last("limit 1"));
        if (operation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "重排操作不存在");
        }
        if (!Objects.equals(operation.getStatus(), LIST_REPLAN_STATUS_PREVIEW)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该重排操作已确认/取消/过期，不能重复确认");
        }
        if (operation.getExpiresAt() != null && LocalDateTime.now().isAfter(operation.getExpiresAt())) {
            aiReplanOperationMapper.update(null, new LambdaUpdateWrapper<AiReplanOperation>()
                    .eq(AiReplanOperation::getId, operation.getId())
                    .eq(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_PREVIEW)
                    .set(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_EXPIRED));
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "重排预览已过期，请重新预览");
        }

        List<AiReplanItem> items = aiReplanItemMapper.selectList(new LambdaQueryWrapper<AiReplanItem>()
                .eq(AiReplanItem::getOperationId, normalizedOperationId));
        List<ListTaskReplanItem> replanItems = items.stream().map(this::toListReplanItem).toList();
        long changedCount = replanItems.stream().filter(ListTaskReplanItem::hasAnyChange).count();
        int updatedCount = applyListReplanItems(replanItems, currentUserId);
        if (updatedCount != changedCount) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "部分任务状态已变化，重排确认失败，请重新预览");
        }

        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, listId)
                .eq(Project::getIsDelete, 0)
                .isNull(Project::getDeletedAt)
                .last("limit 1"));
        if (project != null) {
            syncProjectEndDateIfNeeded(listId, currentUserId, project.getEndDate());
        }

        aiReplanOperationMapper.update(null, new LambdaUpdateWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getId, operation.getId())
                .eq(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_PREVIEW)
                .set(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_CONFIRMED)
                .set(AiReplanOperation::getConfirmedAt, LocalDateTime.now()));
        return updatedCount > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelListReplan(String operationId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (StrUtil.isBlank(operationId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不能为空");
        }

        String normalizedOperationId = operationId.trim();
        AiReplanOperation operation = aiReplanOperationMapper.selectOne(new LambdaQueryWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getOperationId, normalizedOperationId)
                .eq(AiReplanOperation::getUserId, currentUserId)
                .last("limit 1"));
        if (operation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "重排操作不存在");
        }
        if (!Objects.equals(operation.getStatus(), LIST_REPLAN_STATUS_PREVIEW)) {
            return false;
        }

        int rows = aiReplanOperationMapper.update(null, new LambdaUpdateWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getId, operation.getId())
                .eq(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_PREVIEW)
                .set(AiReplanOperation::getStatus, LIST_REPLAN_STATUS_CANCELED)
                .set(AiReplanOperation::getCanceledAt, LocalDateTime.now()));
        return rows > 0;
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
        String cleanedText = sanitizeJsonObjectText(aiRawContent);
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
                                       List<ListTaskReplanItem> replanItems,
                                       List<Task> pendingTasks) {
        LocalDateTime now = LocalDateTime.now();
        AiReplanOperation operation = new AiReplanOperation();
        operation.setOperationId(operationId);
        operation.setUserId(userId);
        operation.setProjectId(listId);
        operation.setStatus(LIST_REPLAN_STATUS_PREVIEW);
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

    private LocalDate resolveReviewDate(String reviewDateText) {
        if (StrUtil.isBlank(reviewDateText)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(reviewDateText.trim());
        } catch (Exception e) {
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
            return selectedTasks;
        }

        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatedByUserId, userId)
                .eq(Task::getDueDate, reviewDate)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreateTime, Task::getId));
    }

    private String buildDailyReviewRenameUserPrompt(LocalDate reviewDate,
                                                    String strategy,
                                                    int maxEdits,
                                                    List<Task> completedTasks,
                                                    List<Task> pendingTasks) {
        JSONArray completedContext = JSONUtil.createArray();
        for (Task task : completedTasks) {
            completedContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("status", task.getStatus())
                    .set("priority", task.getPriority())
                    .set("completedAt", task.getCompletedAt()));
        }

        JSONArray pendingContext = JSONUtil.createArray();
        for (Task task : pendingTasks) {
            pendingContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("status", task.getStatus())
                    .set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate())
                    .set("description", task.getDescription()));
        }

        return "reviewDate: " + reviewDate
                + "\nstrategy: " + strategy
                + "\nmaxEdits: " + maxEdits
                + "\n已完成任务(JSON): " + completedContext
                + "\n未完成任务(JSON): " + pendingContext
                + "\n仅返回未完成任务（pendingTasks）的改名建议。";
    }

    private List<TitleRenameSuggestionItemVO> parseAndValidateRenameSuggestions(String aiRawContent,
                                                                                 List<Task> pendingTasks,
                                                                                 int maxEdits) {
        String cleanedText = sanitizeJsonObjectText(aiRawContent);
        JSONObject resultObj = JSONUtil.parseObj(cleanedText);
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
        return reviewDate.toString().replace("-", "")
                + "_rename_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void saveRenameLogs(Long userId,
                                LocalDate reviewDate,
                                String operationId,
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

    private String normalizeStrategy(String strategy) {
        if (StrUtil.isBlank(strategy)) {
            return "balanced";
        }
        String normalized = strategy.trim().toLowerCase(Locale.ROOT);
        if ("balanced".equals(normalized) || "benefit_first".equals(normalized) || "quick_win".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "strategy 仅支持 balanced、benefit_first、quick_win");
    }

    private ZoneId resolveZoneId(String timezone) {
        if (StrUtil.isBlank(timezone)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "timezone 非法，请使用 IANA 时区，如 Asia/Shanghai");
        }
    }

    private LocalDateTime resolveNow(String nowText, ZoneId zoneId) {
        if (StrUtil.isBlank(nowText)) {
            return LocalDateTime.now(zoneId);
        }
        try {
            return LocalDateTime.parse(nowText.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "now 非法，请使用 ISO-8601 时间格式，如 2026-04-17T10:30:00");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return TODAY_ORDER_LIMIT_DEFAULT;
        }
        return Math.min(limit, TODAY_ORDER_LIMIT_MAX);
    }

    private List<Task> loadTodayTodoTasks(Long userId, AiTodayOrderRequest request, LocalDate today, int limit) {
        List<Long> taskIds = request.getTaskIds();
        if (taskIds != null && !taskIds.isEmpty()) {
            if (taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 至少包含一个有效的正整数ID");
            }
            Set<Long> uniqueIds = permissionService.requireAllTasksReadable(userId, taskIds);

            List<Task> selectedTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .in(Task::getId, uniqueIds)
                    .eq(Task::getIsDelete, 0)
                    .eq(Task::getDueDate, today)
                    .eq(Task::getStatus, 0)
                    .orderByDesc(Task::getPriority)
                    .orderByAsc(Task::getCreateTime, Task::getId));

            if (selectedTasks.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 中无可推荐的今日到期未完成任务");
            }
            return selectedTasks.stream().limit(limit).toList();
        }

        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getCreatedByUserId, userId)
                .eq(Task::getDueDate, today)
                .eq(Task::getStatus, 0)
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getCreateTime, Task::getId)
                .last("limit " + limit));
    }

    private String buildTodayOrderUserPrompt(List<Task> tasks, String strategy, LocalDateTime now, LocalDate today, ZoneId zoneId) {
        JSONArray taskContext = JSONUtil.createArray();
        for (Task task : tasks) {
            taskContext.add(JSONUtil.createObj()
                    .set("taskId", task.getId())
                    .set("title", task.getTitle())
                    .set("description", task.getDescription())
                    .set("priority", task.getPriority())
                    .set("dueDate", task.getDueDate())
                    .set("createTime", task.getCreateTime()));
        }

        return "日期：" + today
                + "\n当前时间：" + now
                + "\n时区：" + zoneId
                + "\n排序策略：" + strategy
                + "\n请覆盖全部任务，不要遗漏，不要重复。"
                + "\n任务列表(JSON)：" + taskContext;
    }

    private AiTodayOrderVO parseAndValidateTodayOrderResult(String aiRawContent,
                                                            List<Task> sourceTasks,
                                                            String strategy,
                                                            LocalDateTime now) {
        String cleanedText = sanitizeJsonObjectText(aiRawContent);
        JSONObject resultObj = JSONUtil.parseObj(cleanedText);
        JSONArray items = resultObj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果缺少 items");
        }
        if (items.size() != sourceTasks.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果任务数量与输入不一致");
        }

        Map<Long, Task> taskMap = sourceTasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Set<Long> seenTaskIds = new HashSet<>();
        List<AiTaskOrderItemVO> orderItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果存在空任务项");
            }
            Long taskId = item.getLong("taskId");
            if (taskId == null || !taskMap.containsKey(taskId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果包含无效 taskId");
            }
            if (!seenTaskIds.add(taskId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果包含重复 taskId");
            }

            Integer difficulty = item.getInt("difficulty");
            Integer cost = item.getInt("cost");
            Integer benefit = item.getInt("benefit");
            Integer estimatedMinutes = item.getInt("estimatedMinutes");
            if (!inRange(difficulty, 1, 5) || !inRange(cost, 1, 5) || !inRange(benefit, 1, 5)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果 difficulty/cost/benefit 超出范围");
            }
            if (!inRange(estimatedMinutes, 10, 240)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果 estimatedMinutes 超出范围");
            }
            String reason = safeTrim(item.getStr("reason"));
            if (StrUtil.isBlank(reason)) {
                reason = "AI建议优先处理";
            }

            orderItems.add(buildOrderItem(taskMap.get(taskId), i + 1, difficulty, cost, benefit, estimatedMinutes, reason, strategy, now));
        }

        if (seenTaskIds.size() != taskMap.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 排序结果未覆盖全部任务");
        }

        AiTodayOrderVO result = new AiTodayOrderVO();
        result.setStrategy(normalizeStrategy(resultObj.getStr("strategy", strategy)));
        result.setItems(orderItems);
        return result;
    }

    private boolean inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private List<AiTaskOrderItemVO> fallbackByRule(List<Task> tasks, String strategy, LocalDateTime now) {
        List<Task> sorted = tasks.stream()
                .sorted(Comparator
                        .comparing(Task::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Task::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Task::getId))
                .toList();

        List<AiTaskOrderItemVO> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Task task = sorted.get(i);
            int priority = task.getPriority() == null ? 0 : task.getPriority();
            int difficulty = priority >= 3 ? 3 : 2;
            int cost = priority >= 2 ? 3 : 2;
            int benefit = clamp(priority + 2, 1, 5);
            int estimatedMinutes = clamp(20 + difficulty * 15, 10, 240);
            result.add(buildOrderItem(task, i + 1, difficulty, cost, benefit, estimatedMinutes,
                    "规则兜底：按优先级与创建时间排序", strategy, now));
        }
        return result;
    }

    private AiTaskOrderItemVO buildOrderItem(Task task,
                                             int rank,
                                             int difficulty,
                                             int cost,
                                             int benefit,
                                             int estimatedMinutes,
                                             String reason,
                                             String strategy,
                                             LocalDateTime now) {
        AiTaskOrderItemVO item = new AiTaskOrderItemVO();
        item.setTaskId(task.getId());
        item.setTitle(task.getTitle());
        item.setRank(rank);
        item.setDifficulty(difficulty);
        item.setCost(cost);
        item.setBenefit(benefit);
        item.setEstimatedMinutes(estimatedMinutes);
        item.setReason(reason);
        item.setScore(calcScore(task.getPriority(), difficulty, cost, benefit, estimatedMinutes, strategy, now, task.getDueDate()));
        return item;
    }

    private int calcScore(Integer priority,
                          Integer difficulty,
                          Integer cost,
                          Integer benefit,
                          Integer estimatedMinutes,
                          String strategy,
                          LocalDateTime now,
                          LocalDate dueDate) {
        int priorityValue = clamp(priority == null ? 0 : priority, TASK_PRIORITY_MIN, TASK_PRIORITY_MAX);
        int d = clamp(difficulty, 1, 5);
        int c = clamp(cost, 1, 5);
        int b = clamp(benefit, 1, 5);
        int m = clamp(estimatedMinutes, 10, 240);

        double score;
        switch (strategy) {
            case "benefit_first" -> score = b * 24 + priorityValue * 8 - c * 7 - d * 5 - m / 12.0;
            case "quick_win" -> score = b * 16 + priorityValue * 8 - c * 10 - d * 10 - m / 6.0;
            default -> score = b * 20 + priorityValue * 10 - c * 8 - d * 6 - m / 10.0;
        }

        if (dueDate != null && dueDate.equals(now.toLocalDate())) {
            score += 5;
        }
        return clamp((int) Math.round(score), 0, 100);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private void normalizeAndValidateDrafts(List<MilestoneDraftVO> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 未生成有效里程碑，请重试");
        }

        for (int i = 0; i < drafts.size(); i++) {
            MilestoneDraftVO milestone = drafts.get(i);
            if (milestone == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑为空");
            }

            String milestoneName = safeTrim(milestone.getName());
            if (StrUtil.isBlank(milestoneName)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑名称为空");
            }
            if (milestoneName.length() > PROJECT_NAME_MAX_LEN) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "AI 结果第" + (i + 1) + "个里程碑名称超长，最多" + PROJECT_NAME_MAX_LEN + "字符");
            }
            milestone.setName(milestoneName);

            List<TaskDraftVO> tasks = milestone.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 结果第" + (i + 1) + "个里程碑缺少任务");
            }

            for (int j = 0; j < tasks.size(); j++) {
                TaskDraftVO task = tasks.get(j);
                if (task == null) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务为空");
                }

                String taskName = safeTrim(task.getName());
                if (StrUtil.isBlank(taskName)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务名称为空");
                }
                if (taskName.length() > TASK_TITLE_MAX_LEN) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务标题超长，最多"
                                    + TASK_TITLE_MAX_LEN + "字符");
                }
                Integer priority = task.getPriority();
                if (priority == null || priority < TASK_PRIORITY_MIN || priority > TASK_PRIORITY_MAX) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务优先级非法，需为0-3整数");
                }

                String dueDate = safeTrim(task.getDueDate());
                if (StrUtil.isBlank(dueDate)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务截止日期为空");
                }
                LocalDate parsedDueDate;
                try {
                    parsedDueDate = LocalDate.parse(dueDate);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1) + "个任务截止日期格式非法，需为yyyy-MM-dd");
                }

                task.setName(taskName);
                task.setPriority(priority);
                task.setDueDate(parsedDueDate.toString());
            }
        }
    }

    private void logDraftLengthRisk(List<MilestoneDraftVO> drafts, String target, boolean detailed) {
        if (drafts == null || drafts.isEmpty()) {
            log.warn("AI任务拆解返回空草稿: target={}, detailed={}", target, detailed);
            return;
        }

        int milestoneCount = 0;
        int taskCount = 0;
        int milestoneNameOverLimitCount = 0;
        int taskNameOverLimitCount = 0;
        int blankMilestoneNameCount = 0;
        int blankTaskNameCount = 0;
        int invalidPriorityCount = 0;
        int blankDueDateCount = 0;
        int invalidDueDateCount = 0;

        for (MilestoneDraftVO milestone : drafts) {
            milestoneCount++;
            if (milestone == null) {
                blankMilestoneNameCount++;
                continue;
            }

            String milestoneName = safeTrim(milestone.getName());
            if (StrUtil.isBlank(milestoneName)) {
                blankMilestoneNameCount++;
            } else if (milestoneName.length() > PROJECT_NAME_MAX_LEN) {
                milestoneNameOverLimitCount++;
            }

            List<TaskDraftVO> tasks = milestone.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                continue;
            }
            for (TaskDraftVO task : tasks) {
                taskCount++;
                if (task == null) {
                    blankTaskNameCount++;
                    continue;
                }
                String taskName = safeTrim(task.getName());
                if (StrUtil.isBlank(taskName)) {
                    blankTaskNameCount++;
                } else if (taskName.length() > TASK_TITLE_MAX_LEN) {
                    taskNameOverLimitCount++;
                }

                Integer priority = task.getPriority();
                if (priority == null || priority < TASK_PRIORITY_MIN || priority > TASK_PRIORITY_MAX) {
                    invalidPriorityCount++;
                }

                String dueDate = safeTrim(task.getDueDate());
                if (StrUtil.isBlank(dueDate)) {
                    blankDueDateCount++;
                } else {
                    try {
                        LocalDate.parse(dueDate);
                    } catch (Exception e) {
                        invalidDueDateCount++;
                    }
                }
            }
        }

        if (milestoneNameOverLimitCount > 0 || taskNameOverLimitCount > 0
                || blankMilestoneNameCount > 0 || blankTaskNameCount > 0
                || invalidPriorityCount > 0 || blankDueDateCount > 0 || invalidDueDateCount > 0) {
            log.warn("AI任务拆解草稿存在导入风险: target={}, detailed={}, milestones={}, tasks={}, overMilestoneNames={}, overTaskNames={}, blankMilestoneNames={}, blankTaskNames={}, invalidPriority={}, blankDueDate={}, invalidDueDate={}",
                    target,
                    detailed,
                    milestoneCount,
                    taskCount,
                    milestoneNameOverLimitCount,
                    taskNameOverLimitCount,
                    blankMilestoneNameCount,
                    blankTaskNameCount,
                    invalidPriorityCount,
                    blankDueDateCount,
                    invalidDueDateCount);
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }

    private AiDraft createDraft(Long userId, String scene, String payloadJson, String inputHash) {
        AiDraft draft = new AiDraft();
        draft.setDraftId(UUID.randomUUID().toString().replace("-", ""));
        draft.setUserId(userId);
        draft.setScene(scene);
        draft.setPayloadJson(payloadJson);
        draft.setInputHash(inputHash);
        draft.setStatus(AiDraftStatusEnum.PREVIEW.getValue());
        draft.setExpireAt(LocalDateTime.now().plusMinutes(AI_DRAFT_EXPIRE_MINUTES));
        int rows = aiDraftMapper.insert(draft);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿创建失败");
        }
        return draft;
    }

    private AiDraft getDraftByUserAndOptionalScene(Long userId, String draftId, String scene) {
        LambdaQueryWrapper<AiDraft> wrapper = new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, draftId)
                .eq(AiDraft::getUserId, userId);
        if (StrUtil.isNotBlank(scene)) {
            wrapper.eq(AiDraft::getScene, scene);
        }
        wrapper.last("limit 1");
        AiDraft draft = aiDraftMapper.selectOne(wrapper);
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }
        return draft;
    }

    private AiDraft getDraftByUserAndScene(Long userId, String draftId, String scene) {
        AiDraft draft = aiDraftMapper.selectOne(new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, draftId)
                .eq(AiDraft::getUserId, userId)
                .eq(AiDraft::getScene, scene)
                .last("limit 1"));
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }
        return draft;
    }

    private AiDraftConfirmLog getConfirmLog(Long userId, String draftId, String operationId) {
        return aiDraftConfirmLogMapper.selectOne(new LambdaQueryWrapper<AiDraftConfirmLog>()
                .eq(AiDraftConfirmLog::getUserId, userId)
                .eq(AiDraftConfirmLog::getDraftId, draftId)
                .eq(AiDraftConfirmLog::getOperationId, operationId)
                .last("limit 1"));
    }

    private void validateDraftCanConfirm(AiDraft draft) {
        if (refreshDraftExpiredIfNecessary(draft)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，请重新预览");
        }
        if (AiDraftStatusEnum.isPreview(draft.getStatus())) {
            return;
        }
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已确认，请勿重复提交");
        }
        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已取消，请重新生成");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，请重新预览");
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "草稿状态异常，无法确认：" + AiDraftStatusEnum.getText(draft.getStatus()));
    }

    private boolean refreshDraftExpiredIfNecessary(AiDraft draft) {
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            return false;
        }
        if (draft.getExpireAt() != null && LocalDateTime.now().isAfter(draft.getExpireAt())) {
            int rows = aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                    .eq(AiDraft::getId, draft.getId())
                    .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                    .set(AiDraft::getStatus, AiDraftStatusEnum.EXPIRED.getValue()));
            if (rows > 0) {
                draft.setStatus(AiDraftStatusEnum.EXPIRED.getValue());
            }
            return true;
        }
        return false;
    }

    private void markDraftConfirmed(Long draftDbId) {
        int rows = aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getId, draftDbId)
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .set(AiDraft::getStatus, AiDraftStatusEnum.CONFIRMED.getValue())
                .set(AiDraft::getConfirmedAt, LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿状态更新失败");
        }
    }

    private void insertConfirmLog(Long userId, String draftId, String operationId, String scene, Long businessId) {
        AiDraftConfirmLog logEntity = new AiDraftConfirmLog();
        logEntity.setUserId(userId);
        logEntity.setDraftId(draftId);
        logEntity.setOperationId(operationId);
        logEntity.setScene(scene);
        logEntity.setBusinessId(businessId);
        try {
            aiDraftConfirmLogMapper.insert(logEntity);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "确认日志写入失败");
        }
    }

    private AiDraftConfirmVO buildConfirmVO(boolean replay, Long businessId) {
        AiDraftConfirmVO vo = new AiDraftConfirmVO();
        vo.setSuccess(true);
        vo.setIdempotentReplay(replay);
        vo.setBusinessId(businessId);
        return vo;
    }

    private String extractReviewText(String polished) {
        if (StrUtil.isBlank(polished)) {
            return "";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(sanitizeJsonObjectText(polished));
            return safeTrim(obj.getStr("review"));
        } catch (Exception e) {
            return safeTrim(polished);
        }
    }

    private int getNextProjectOrderNo(Long userId) {
        Project latest = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("limit 1"));
        if (latest == null || latest.getOrderNo() == null) {
            return 0;
        }
        return latest.getOrderNo() + 1;
    }

    private String buildInputHash(String raw) {
        return Integer.toHexString(Objects.hashCode(raw));
    }

    /**
     * 创建 AI 调用日志
     *
     * @param userId
     * @param modelName
     * @param promptTemplate
     * @param requestText
     * @param retryCount
     * @return
     */
    private Long createAiCallLogSafely(Long userId,
                                       String modelName,
                                       AiPromptTemplate promptTemplate,
                                       String requestText,
                                       Integer retryCount) {
        try {
            return aiCallLogService.createRunningLog(new AiCallLogCreateCommand(
                    userId,
                    promptTemplate.scene(),
                    modelName,
                    promptTemplate.code(),
                    promptTemplate.templateId(),
                    promptTemplate.version(),
                    promptTemplate.source().getCode(),
                    requestText,
                    retryCount
            ));
        } catch (Exception e) {
            log.warn("AI调用日志创建失败: scene={}, model={}", promptTemplate.scene(), modelName, e);
            return null;
        }
    }

    private AiInvocationResult invokeAiWithLog(Long logId,
                                               String modelName,
                                               String systemPrompt,
                                               String userPrompt,
                                               long startTime) {
        try {
            // 调用 AI 模型，并保存调用记录
            AiInvocationResult result = aiModelClient.invoke(modelName, systemPrompt, userPrompt);
            // 更新调用元数据
            updateExecutionMetadataSafely(logId, result.actualModel(), result.retryCount());
            return result;
        } catch (AiInvocationException e) {
            // 如果 AI 模型调用异常，则将其记录
            markInvocationFailedSafely(logId, e, elapsedSince(startTime));
            throw e;
        } catch (Exception e) {
            AiInvocationException wrapped = new AiInvocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR,
                    modelName,
                    0,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI 调用发生未分类异常: model=" + modelName,
                    e
            );
            markInvocationFailedSafely(logId, wrapped, elapsedSince(startTime));
            throw wrapped;
        }
    }

    private void updateExecutionMetadataSafely(Long logId, String actualModel, Integer retryCount) {
        try {
            aiCallLogService.updateExecutionMetadata(logId, actualModel, retryCount);
        } catch (Exception e) {
            log.warn("AI调用日志执行元数据更新失败: logId={}", logId, e);
        }
    }

    private void markInvocationFailedSafely(Long logId,
                                            AiInvocationException exception,
                                            Long costTimeMs) {
        updateExecutionMetadataSafely(logId, exception.getModelName(), exception.getRetryCount());
        if (exception.getFailureType() == AiFailureTypeEnum.TIMEOUT) {
            markAiCallTimeoutSafely(logId, exception.getSafeMessage(), costTimeMs);
            return;
        }
        if (exception.getFailureType() == AiFailureTypeEnum.INVALID_RESPONSE) {
            markAiCallParseFailedSafely(logId, null, exception.getSafeMessage(), costTimeMs);
            return;
        }
        markAiCallFailedSafely(logId, exception.getSafeMessage(), costTimeMs);
    }

    private BusinessException toBusinessException(AiInvocationException exception) {
        ErrorCode errorCode = switch (exception.getFailureType()) {
            case CONFIG_ERROR -> ErrorCode.AI_CONFIG_ERROR;
            case TIMEOUT -> ErrorCode.AI_REQUEST_TIMEOUT;
            case INVALID_RESPONSE -> ErrorCode.AI_RESPONSE_INVALID;
            default -> ErrorCode.AI_SERVICE_UNAVAILABLE;
        };
        return new BusinessException(errorCode, exception.getSafeMessage());
    }

    private void markAiCallSuccessSafely(Long logId, String responseText, Long costTimeMs) {
        try {
            aiCallLogService.markSuccess(logId, responseText, costTimeMs);
        } catch (Exception e) {
            log.warn("AI调用日志更新成功状态失败: logId={}", logId, e);
        }
    }

    private void markAiCallFailedSafely(Long logId, String errorMessage, Long costTimeMs) {
        try {
            aiCallLogService.markFailed(logId, errorMessage, costTimeMs);
        } catch (Exception e) {
            log.warn("AI调用日志更新失败状态失败: logId={}", logId, e);
        }
    }

    private void markAiCallTimeoutSafely(Long logId, String errorMessage, Long costTimeMs) {
        try {
            aiCallLogService.markTimeout(logId, errorMessage, costTimeMs);
        } catch (Exception e) {
            log.warn("AI调用日志更新超时状态失败: logId={}", logId, e);
        }
    }

    private void markAiCallParseFailedSafely(Long logId, String responseText, String errorMessage, Long costTimeMs) {
        try {
            aiCallLogService.markParseFailed(logId, responseText, errorMessage, costTimeMs);
        } catch (Exception e) {
            log.warn("AI调用日志更新解析失败状态失败: logId={}", logId, e);
        }
    }

    private long elapsedSince(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private String buildAiCallRequestText(String systemPrompt, String userPrompt) {
        return JSONUtil.createObj()
                .set("systemPrompt", systemPrompt)
                .set("userPrompt", userPrompt)
                .toString();
    }

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    private String sanitizeJsonArrayText(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
        }

        String cleaned = content.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int startIndex = cleaned.indexOf('[');
        int endIndex = cleaned.lastIndexOf(']');
        if (startIndex >= 0 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1);
        }
        return cleaned;
    }

    private String sanitizeJsonObjectText(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 返回内容为空");
        }

        String cleaned = content.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int startIndex = cleaned.indexOf('{');
        int endIndex = cleaned.lastIndexOf('}');
        if (startIndex >= 0 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1);
        }
        return cleaned;
    }

    private String resolveModel(String preferredModel) {
        String model = StrUtil.isNotBlank(preferredModel) ? preferredModel.trim() : safeTrim(aiProperties.getModel());
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.AI_CONFIG_ERROR, "AI 服务配置异常，请联系管理员");
        }
        return model;
    }
}

