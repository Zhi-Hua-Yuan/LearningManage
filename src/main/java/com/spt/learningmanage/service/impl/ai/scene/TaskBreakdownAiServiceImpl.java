package com.spt.learningmanage.service.impl.ai.scene;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.ai.pipeline.AiExecutionCommand;
import com.spt.learningmanage.ai.pipeline.AiInvocationPipeline;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.ai.scene.TaskBreakdownAiService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TaskBreakdownAiServiceImpl extends AiSceneSupport implements TaskBreakdownAiService {

    private static final Logger log = LoggerFactory.getLogger(TaskBreakdownAiServiceImpl.class);
    private static final int PROJECT_NAME_MAX_LEN = 100;
    private static final int TASK_TITLE_MAX_LEN = 60;
    private static final int TASK_PRIORITY_MIN = 0;
    private static final int TASK_PRIORITY_MAX = 3;

    private final ProjectMapper projectMapper;
    private final MilestoneMapper milestoneMapper;
    private final AiInvocationPipeline aiInvocationPipeline;
    private final PermissionService permissionService;
    private final TaskCreationService taskCreationService;
    private final AiDraftLifecycleService draftLifecycleService;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public TaskBreakdownAiServiceImpl(ProjectMapper projectMapper,
                                      MilestoneMapper milestoneMapper,
                                      AiInvocationPipeline aiInvocationPipeline,
                                      PermissionService permissionService,
                                      TaskCreationService taskCreationService,
                                      AiDraftLifecycleService draftLifecycleService,
                                      AiModelSelector modelSelector,
                                      AiJsonResponseSanitizer jsonSanitizer) {
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.permissionService = permissionService;
        this.taskCreationService = taskCreationService;
        this.draftLifecycleService = draftLifecycleService;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Override
    public List<MilestoneDraftVO> generateTaskBreakdown(String target, String description,
                                                        String duration, boolean detailed) {
        if (StrUtil.hasBlank(target, duration)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标和周期不能为空，描述可为空");
        }
        Long userId = currentUserId();
        String normalizedTarget = target.trim();
        if (normalizedTarget.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标长度不能超过100个字符");
        }
        String userPrompt = String.format("目标：%s，周期：%s，今天日期：%s。",
                normalizedTarget, duration.trim(), LocalDate.now());
        if (StrUtil.isNotBlank(description)) {
            userPrompt += String.format("补充描述：%s。", description.trim());
        }
        AiPromptCodeEnum promptCode = detailed
                ? AiPromptCodeEnum.TASK_BREAKDOWN_DETAILED
                : AiPromptCodeEnum.TASK_BREAKDOWN_DEFAULT;
        try {
            return aiInvocationPipeline.execute(new AiExecutionCommand(
                    userId, modelSelector.breakdownModel(), promptCode, userPrompt,
                    "AI 任务拆解结果格式异常"
            ), aiRawContent -> {
                JSONArray jsonArray = JSONUtil.parseArray(jsonSanitizer.sanitizeArray(aiRawContent));
                List<MilestoneDraftVO> result = JSONUtil.toList(jsonArray, MilestoneDraftVO.class);
                normalizeAndValidateDrafts(result);
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
        List<MilestoneDraftVO> drafts = generateTaskBreakdown(
                request.getTarget(), request.getDescription(), request.getDuration(), detailed);
        Long userId = currentUserId();
        JSONObject payload = JSONUtil.createObj()
                .set("target", request.getTarget())
                .set("description", request.getDescription())
                .set("duration", request.getDuration())
                .set("detailed", detailed)
                .set("milestones", drafts);
        AiDraft draft = draftLifecycleService.createDraft(
                userId, AiSceneEnum.TASK_BREAKDOWN.getCode(), payload.toString(),
                draftLifecycleService.buildInputHash(payload.toString()));
        AiBreakdownPreviewVO vo = new AiBreakdownPreviewVO();
        vo.setDraftId(draft.getDraftId());
        vo.setExpireAt(draft.getExpireAt());
        vo.setMilestones(drafts);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDraftConfirmVO confirmTaskBreakdown(String draftId, String operationId,
                                                 String projectName, String projectGoal) {
        Long userId = currentUserId();
        AiDraft draft = draftLifecycleService.requireDraft(
                userId, draftId, AiSceneEnum.TASK_BREAKDOWN.getCode());
        AiDraftConfirmLog replay = draftLifecycleService.findConfirmLog(userId, draftId, operationId);
        if (replay != null) {
            return draftLifecycleService.buildConfirmResult(true, replay.getBusinessId());
        }
        draftLifecycleService.requireConfirmable(draft);

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
        project.setProgress(BigDecimal.ZERO);
        project.setIsDelete(0);
        project.setOrderNo(getNextProjectOrderNo(userId));
        if (projectMapper.insert(project) != 1 || project.getId() == null) {
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
            milestone.setProgress(BigDecimal.ZERO);
            milestone.setIsDelete(0);
            milestone.setDeleteSource(DeleteSourceConstant.NORMAL);
            if (milestoneMapper.insert(milestone) != 1 || milestone.getId() == null) {
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
        draftLifecycleService.markConfirmed(draft.getId());
        draftLifecycleService.insertConfirmLog(userId, draftId, operationId,
                AiSceneEnum.TASK_BREAKDOWN.getCode(), project.getId());
        return draftLifecycleService.buildConfirmResult(false, project.getId());
    }

    private int getNextProjectOrderNo(Long userId) {
        Project latest = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("limit 1"));
        return latest == null || latest.getOrderNo() == null ? 0 : latest.getOrderNo() + 1;
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
