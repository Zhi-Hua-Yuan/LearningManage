package com.spt.learningmanage.service.impl.ai.draft;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.ProjectConstant;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.model.dto.ai.draft.TaskBreakdownConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;
import com.spt.learningmanage.model.vo.milestone.TaskDraftVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class TaskBreakdownDraftHandler implements AiDraftHandler<TaskBreakdownConfirmationContext> {

    private static final int PROJECT_NAME_MAX_LEN = 100;
    private static final int TASK_TITLE_MAX_LEN = 60;

    private final ProjectMapper projectMapper;
    private final MilestoneMapper milestoneMapper;
    private final PermissionService permissionService;
    private final TaskCreationService taskCreationService;

    public TaskBreakdownDraftHandler(ProjectMapper projectMapper,
                                     MilestoneMapper milestoneMapper,
                                     PermissionService permissionService,
                                     TaskCreationService taskCreationService) {
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.permissionService = permissionService;
        this.taskCreationService = taskCreationService;
    }

    @Override
    public String scene() {
        return AiSceneEnum.TASK_BREAKDOWN.getCode();
    }

    @Override
    public int currentSchemaVersion() {
        return 1;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    @Override
    public Class<TaskBreakdownConfirmationContext> contextType() {
        return TaskBreakdownConfirmationContext.class;
    }

    @Override
    public Long apply(AiDraft draft, TaskBreakdownConfirmationContext context) {
        JSONObject payload = parsePayload(draft);
        JSONArray milestonesJson = payload.getJSONArray("milestones");
        if (milestonesJson == null || milestonesJson.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿缺少里程碑数据");
        }
        List<MilestoneDraftVO> milestoneDrafts;
        try {
            milestoneDrafts = JSONUtil.toList(milestonesJson, MilestoneDraftVO.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿里程碑数据损坏");
        }
        validateDrafts(milestoneDrafts);

        String target = safeTrim(payload.getStr("target"));
        String finalProjectName = StrUtil.isNotBlank(context.projectName())
                ? context.projectName().trim() : target;
        if (StrUtil.isBlank(finalProjectName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectName 不能为空");
        }
        if (finalProjectName.length() > PROJECT_NAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectName 过长");
        }

        Long userId = draft.getUserId();
        Project project = new Project();
        project.setUserId(userId);
        project.setName(finalProjectName);
        project.setGoal(StrUtil.isNotBlank(context.projectGoal())
                ? context.projectGoal().trim() : safeTrim(payload.getStr("description")));
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
        return project.getId();
    }

    private JSONObject parsePayload(AiDraft draft) {
        try {
            return JSONUtil.parseObj(draft.getPayloadJson());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿内容损坏，请重新生成");
        }
    }

    private int getNextProjectOrderNo(Long userId) {
        Project latest = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getOrderNo)
                .last("limit 1"));
        return latest == null || latest.getOrderNo() == null ? 0 : latest.getOrderNo() + 1;
    }

    private void validateDrafts(List<MilestoneDraftVO> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿缺少有效里程碑");
        }
        for (MilestoneDraftVO milestone : drafts) {
            String name = milestone == null ? "" : safeTrim(milestone.getName());
            if (StrUtil.isBlank(name) || name.length() > PROJECT_NAME_MAX_LEN
                    || milestone.getTasks() == null || milestone.getTasks().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿里程碑数据不合法");
            }
            milestone.setName(name);
            for (TaskDraftVO task : milestone.getTasks()) {
                String taskName = task == null ? "" : safeTrim(task.getName());
                if (StrUtil.isBlank(taskName) || taskName.length() > TASK_TITLE_MAX_LEN
                        || task.getPriority() == null || task.getPriority() < 0 || task.getPriority() > 3) {
                    throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿任务数据不合法");
                }
                try {
                    LocalDate.parse(task.getDueDate());
                } catch (Exception exception) {
                    throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿任务截止日期不合法");
                }
                task.setName(taskName);
            }
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
