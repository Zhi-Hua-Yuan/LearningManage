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
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.dto.ai.draft.WeeklyReviewPolishConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiPolishPreviewVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.service.ai.scene.WeeklyReviewAiService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.service.ai.support.AiJsonResponseSanitizer;
import com.spt.learningmanage.service.ai.support.AiModelSelector;
import com.spt.learningmanage.service.impl.ai.support.AiSceneSupport;
import com.spt.learningmanage.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WeeklyReviewAiServiceImpl extends AiSceneSupport implements WeeklyReviewAiService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReviewAiServiceImpl.class);
    private static final int MAX_POLISH_TASK_COUNT = 50;
    private static final String EMPTY_REFLECTION_PLACEHOLDER = "（用户未填写反思）";

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final AiInvocationPipeline aiInvocationPipeline;
    private final PermissionService permissionService;
    private final AiDraftLifecycleService draftLifecycleService;
    private final AiDraftConfirmationService draftConfirmationService;
    private final AiModelSelector modelSelector;
    private final AiJsonResponseSanitizer jsonSanitizer;

    public WeeklyReviewAiServiceImpl(TaskMapper taskMapper,
                                     ProjectMapper projectMapper,
                                     AiInvocationPipeline aiInvocationPipeline,
                                     PermissionService permissionService,
                                     AiDraftLifecycleService draftLifecycleService,
                                     AiDraftConfirmationService draftConfirmationService,
                                     AiModelSelector modelSelector,
                                     AiJsonResponseSanitizer jsonSanitizer) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.aiInvocationPipeline = aiInvocationPipeline;
        this.permissionService = permissionService;
        this.draftLifecycleService = draftLifecycleService;
        this.draftConfirmationService = draftConfirmationService;
        this.modelSelector = modelSelector;
        this.jsonSanitizer = jsonSanitizer;
    }

    @Override
    public String polishWeeklyReview(List<Long> taskIds, String reflection) {
        return polishWeeklyReview(taskIds, reflection, null);
    }

    private String polishWeeklyReview(List<Long> taskIds, String reflection, String traceId) {
        Long currentUserId = UserHolder.get();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }
        List<Long> validTaskIds = taskIds == null
                ? new ArrayList<>()
                : taskIds.stream().filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(ArrayList::new));
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
        if (!missingIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "传入的任务均须满足当前业务条件，不能部分放行");
        }

        int actualTaskCount = taskList.size();
        List<Task> limitedTaskList = taskList.stream().limit(MAX_POLISH_TASK_COUNT).toList();
        Set<Long> projectIds = limitedTaskList.stream().map(Task::getProjectId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Set<Long> readableProjectIds = permissionService.resolveProjectScopes(currentUserId, projectIds).keySet();
        Map<Long, Project> projectMap = readableProjectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getId, readableProjectIds)
                        .eq(Project::getIsDelete, 0)
                        .isNull(Project::getDeletedAt))
                .stream().collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));

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
        try {
            return aiInvocationPipeline.execute(new AiExecutionCommand(
                    currentUserId, modelSelector.polishModel(), AiPromptCodeEnum.WEEKLY_POLISH_DEFAULT,
                    userPrompt, "AI 周总结润色结果格式异常", traceId
            ), aiRawContent -> {
                JSONObject resultObj = JSONUtil.parseObj(jsonSanitizer.sanitizeObject(aiRawContent));
                String review = resultObj.getStr("review");
                if (StrUtil.isBlank(review)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "周总结润色结果缺少 review 字段，请重试");
                }
                return JSONUtil.createObj().set("review", review).toString();
            }).data();
        } catch (AiInvocationException exception) {
            log.warn("AI 周总结润色调用失败: type={}, model={}",
                    exception.getFailureType(), exception.getModelName(), exception);
            throw toBusinessException(exception);
        } catch (AiResponseProcessingException exception) {
            log.warn("AI 周总结润色结果处理失败: type={}", exception.getFailureType(), exception);
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "AI 周总结润色结果格式异常，请重试");
        }
    }

    @Override
    public AiPolishPreviewVO previewWeeklyPolish(AiPolishRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String polished = polishWeeklyReview(request.getTaskIds(), request.getReflection(), traceId);
        Long userId = currentUserId();
        JSONObject payload = JSONUtil.createObj()
                .set("taskIds", request.getTaskIds())
                .set("reflection", request.getReflection())
                .set("polished", polished);
        AiDraft draft = draftLifecycleService.createDraft(new AiDraftCreateCommand(
                userId, AiSceneEnum.WEEKLY_POLISH.getCode(), payload.toString(),
                draftLifecycleService.buildInputHash(payload.toString()), 1, traceId));
        AiPolishPreviewVO vo = new AiPolishPreviewVO();
        vo.setDraftId(draft.getDraftId());
        vo.setExpireAt(draft.getExpireAt());
        vo.setReview(extractReviewText(polished));
        return vo;
    }

    @Override
    public AiDraftConfirmVO confirmWeeklyPolish(String draftId, String operationId, Long reviewId) {
        Long userId = currentUserId();
        return draftConfirmationService.confirm(new AiDraftConfirmationCommand(
                userId, draftId, operationId, AiSceneEnum.WEEKLY_POLISH.getCode(),
                new WeeklyReviewPolishConfirmationContext(reviewId)));
    }

    private String extractReviewText(String polished) {
        if (StrUtil.isBlank(polished)) {
            return "";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(jsonSanitizer.sanitizeObject(polished));
            return safeTrim(obj.getStr("review"));
        } catch (Exception exception) {
            return safeTrim(polished);
        }
    }
}
