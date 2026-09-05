package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.service.ai.scene.TaskBreakdownAiService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskBreakdownAiServiceImpl extends AiSceneSupport implements TaskBreakdownAiService {

    private static final Logger log = LoggerFactory.getLogger(TaskBreakdownAiServiceImpl.class);
    private static final int PROJECT_NAME_MAX_LEN = 100;
    private static final int TASK_TITLE_MAX_LEN = 60;
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)\\s*(天|周|个月|月|年)$");

    private final AiInvocationPipeline aiInvocationPipeline;
    private final AiDraftLifecycleService draftLifecycleService;
    private final AiDraftConfirmationService draftConfirmationService;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public TaskBreakdownAiServiceImpl(AiInvocationPipeline aiInvocationPipeline,
                                      AiDraftLifecycleService draftLifecycleService,
                                      AiDraftConfirmationService draftConfirmationService,
                                      AiModelSelector modelSelector,
                                      AiJsonResponseSanitizer jsonSanitizer) {
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.draftLifecycleService = draftLifecycleService;
        this.draftConfirmationService = draftConfirmationService;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description,
                                                        String duration, boolean detailed) {
        return generateTaskBreakdown(target, description, duration, detailed, null);
    }

    private List<MilestoneDraftVO> generateTaskBreakdown(String target, String description,
                                                         String duration, boolean detailed,
                                                         String traceId) {
        if (StrUtil.hasBlank(target, duration)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空，描述可为空");
        }
        Long userId = currentUserId();
        String normalizedTarget = target.trim();
        if (normalizedTarget.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标长度不能超过100个字符");
        }
        String normalizedDuration = duration.trim();
        LocalDate today = LocalDate.now();
        LocalDate planningEndDate = resolvePlanningEndDate(today, normalizedDuration);
        String userPrompt = planningEndDate == null
                ? String.format("目标：%s，原始周期：%s，今天日期：%s。所有截止日期必须严格符合原始周期。",
                normalizedTarget, normalizedDuration, today)
                : String.format("目标：%s，原始周期：%s，今天日期（含）：%s，最晚截止日期（含）：%s。"
                        + "所有 dueDate 必须位于这两个日期之间，绝不能晚于最晚截止日期。",
                normalizedTarget, normalizedDuration, today, planningEndDate);
        if (StrUtil.isNotBlank(description)) {
            userPrompt += String.format("补充描述：%s。", description.trim());
        }
        AiPromptCodeEnum promptCode = detailed
                ? AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED
                : AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT;
        try {
            return aiInvocationPipeline.execute(new AiExecutionCommand(
                    userId, modelSelector.breakdownModel(), promptCode, userPrompt,
                    "AI 任务拆解结果格式异常", traceId
            ), aiRawContent -> {
                JSONArray jsonArray = JSONUtil.parseArray(jsonSanitizer.sanitizeArray(aiRawContent));
                List<MilestoneDraftVO> result = JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
                normalizeAndValidateDrafts(result, today, planningEndDate);
                logDraftLengthRisk(result, normalizedTarget, detailed);
                if (result == null || result.isEmpty()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 未生成可用草稿，请调整描述后重试（避免与名称长度约束冲突）");
                }
                return result;
            }).data();
        } catch (AiInvocationException exception) {
            log.warn("AI 任务拆解调用失败: type={}, model={}",
                    exception.getFailureType(), exception.getModelName(), exception);
            throw toBusinessException(exception);
        } catch (AiResponseProcessingException exception) {
            log.warn("AI 任务拆解结果处理失败: type={}", exception.getFailureType(), exception);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 任务拆解结果格式异常，请重试");
        }
    }

    @Override
    public AiBreakdownPreviewVO previewTaskBreakdown(AiBreakdownRequest request) {
        boolean detailed = request.getDetailed() != null && request.getDetailed();
        String traceId = TraceContext.explicitOrCurrent(null);
        List<MilestoneDraftVO> drafts = generateTaskBreakdown(
                request.getTarget(), request.getDescription(), request.getDuration(), detailed, traceId);
        Long userId = currentUserId();
        JSONObject payload = JSONUtil.createObj()
                .set("target", request.getTarget())
                .set("description", request.getDescription())
                .set("duration", request.getDuration())
                .set("detailed", detailed)
                .set("milestones", drafts);
        AiDraft draft = draftLifecycleService.createDraft(new AiDraftCreateCommand(
                userId, AiSceneEnum.TASK_BREAKDOWN.getCode(), payload.toString(),
                draftLifecycleService.buildInputHash(payload.toString()), 1, traceId));
        AiBreakdownPreviewVO vo = new AiBreakdownPreviewVO();
        vo.setDraftId(draft.getDraftId());
        vo.setExpireAt(draft.getExpireAt());
        vo.setMilestones(drafts);
        return vo;
    }

    @Override
    public AiDraftConfirmVO confirmTaskBreakdown(String draftId, String operationId,
                                                 String projectName, String projectGoal) {
        Long userId = currentUserId();
        return draftConfirmationService.confirm(new AiDraftConfirmationCommand(
                userId, draftId, operationId, AiSceneEnum.TASK_BREAKDOWN.getCode(),
                new TaskBreakdownConfirmationContext(projectName, projectGoal)));
    }

    private void normalizeAndValidateDrafts(List<MilestoneDraftVO> drafts,
                                            LocalDate planningStartDate,
                                            LocalDate planningEndDate) {
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
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1)
                                    + "个任务标题超长，最多" + TASK_TITLE_MAX_LEN + "字符");
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
                } catch (Exception exception) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1)
                                    + "个任务截止日期格式非法，需为yyyy-MM-dd");
                }
                if (parsedDueDate.isBefore(planningStartDate)
                        || planningEndDate != null && parsedDueDate.isAfter(planningEndDate)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 结果第" + (i + 1) + "个里程碑第" + (j + 1)
                                    + "个任务截止日期超出计划周期");
                }
                task.setName(taskName);
                task.setPriority(priority);
                task.setDueDate(parsedDueDate.toString());
            }
        }
    }

    private LocalDate resolvePlanningEndDate(LocalDate startDate, String duration) {
        Matcher matcher = DURATION_PATTERN.matcher(duration);
        if (!matcher.matches()) {
            return null;
        }
        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }
        try {
            return switch (matcher.group(2)) {
                case "天" -> startDate.plusDays(amount);
                case "周" -> startDate.plusWeeks(amount);
                case "个月", "月" -> startDate.plusMonths(amount);
                case "年" -> startDate.plusYears(amount);
                default -> null;
            };
        } catch (DateTimeException | ArithmeticException exception) {
            return null;
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
                    } catch (Exception exception) {
                        invalidDueDateCount++;
                    }
                }
            }
        }
        if (milestoneNameOverLimitCount > 0 || taskNameOverLimitCount > 0
                || blankMilestoneNameCount > 0 || blankTaskNameCount > 0
                || invalidPriorityCount > 0 || blankDueDateCount > 0 || invalidDueDateCount > 0) {
            log.warn("AI任务拆解草稿存在导入风险: target={}, detailed={}, milestones={}, tasks={}, overMilestoneNames={}, overTaskNames={}, blankMilestoneNames={}, blankTaskNames={}, invalidPriority={}, blankDueDate={}, invalidDueDate={}",
                    target, detailed, milestoneCount, taskCount, milestoneNameOverLimitCount,
                    taskNameOverLimitCount, blankMilestoneNameCount, blankTaskNameCount,
                    invalidPriorityCount, blankDueDateCount, invalidDueDateCount);
        }
    }
}
